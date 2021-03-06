package server.api.main;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;

import broker.Broker;
import broker.Informal;
import broker.Informal.Builder;
import broker.acl.ACL;
import broker.acl.EmptyACL;
import rasterdb.Band;
import rasterdb.RasterDB;
import remotetask.RemoteTaskExecutor;
import remotetask.rasterdb.ImportBySpec;
import remotetask.rasterdb.ImportRemoteTask;
import remotetask.rasterdb.ImportSpec;
import server.api.APIHandler;
import server.api.main.APIHandler_inspect.Strategy;
import server.api.main.ChunkedUploader.ChunkedUpload;
import util.JsonUtil;
import util.TimeUtil;
import util.Web;
import util.collections.vec.Vec;
import util.raster.GdalReader;

public class APIHandler_import extends APIHandler {
	private static final Logger log = LogManager.getLogger();

	private final ChunkedUploader chunkedUploader;

	public APIHandler_import(Broker broker, ChunkedUploader chunkedUploader) {
		super(broker, "import");
		this.chunkedUploader = chunkedUploader;
	}



	@Override
	protected void handle(String target, Request request, Response response) throws IOException {

		JSONObject json = new JSONObject(Web.requestContentToString(request));
		JSONObject specification = json.getJSONObject("specification");
		log.info(specification);
		String filename = specification.getString("filename");
		ChunkedUpload chunkedUpload = chunkedUploader.map.get(filename);
		Path path;
		if(chunkedUpload == null) {
			//throw new RuntimeException("file not found");
			log.warn("old session ? ");
			path = Paths.get("temp/raster", filename);
		} else {
			path = chunkedUpload.path;
		}
		
		String id = specification.getString("id");
		UserIdentity userIdentity = Web.getUserIdentity(request);
		if(!EmptyACL.ADMIN.isAllowed(userIdentity) && broker.hasRasterdb(id)) {
			log.info("not admin");
			RasterDB rasterdb = broker.getRasterdb(id);
			rasterdb.checkMod(userIdentity);
			log.info("allowed");
		}

		ImportSpec spec = new ImportSpec();
		spec.parse(specification);
		ImportRemoteTask importRemoteTask = ImportBySpec.importPerpare(broker, path, id, spec);
		
		RemoteTaskExecutor.insertToExecute(importRemoteTask);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MIME_JSON);
		JSONWriter res = new JSONWriter(response.getWriter());
		res.object();
		res.key("remote_task");
		res.object();	
		res.key("id");
		res.value(importRemoteTask.id);
		res.endObject();
		res.endObject();		
	}
	

}
