package tnoodleServerHandler;

import static net.gnehzr.tnoodle.utils.Utils.azzert;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;

import net.gnehzr.tnoodle.server.SafeHttpHandler;
import net.gnehzr.tnoodle.utils.Utils;

import com.petebevin.markdown.MarkdownProcessor;
import com.sun.net.httpserver.HttpExchange;

public class DirectoryHandler extends SafeHttpHandler {
	private static final String PLUGIN_DIRECTORY = DirectoryHandler.class.getPackage().getName();
	private static final MarkdownProcessor mp = new MarkdownProcessor();
	
	private static MimetypesFileTypeMap mimes = new MimetypesFileTypeMap();
	static {
		mimes.addMimeTypes("text/css css");
		mimes.addMimeTypes("text/html html htm");
		mimes.addMimeTypes("text/plain txt");
		
		mimes.addMimeTypes("image/png png");
		mimes.addMimeTypes("image/gif gif");
		mimes.addMimeTypes("image/vnd.microsoft.icon ico");
		
		mimes.addMimeTypes("application/x-font-ttf ttf");
		
		mimes.addMimeTypes("application/x-javascript js");
		mimes.addMimeTypes("application/json json");
		mimes.addMimeTypes("application/octet-stream *");
	}
	
	private static String headInjectCode;
	public static void setHeadInjectCode(String code) {
		headInjectCode = code;
	}
	
	private static class CachedFileInfo {
		byte[] data;
		String contentType;
	}

	private static Map<String, CachedFileInfo> cachedFiles;
	public static void setCachingEnabled(boolean enabled) {
		boolean caching = (cachedFiles != null);
		if(enabled == caching) {
			return;
		}
		if(enabled) {
			cachedFiles = new HashMap<String, CachedFileInfo>();
		} else {
			cachedFiles = null;
		}
	}
	static {
		setCachingEnabled(true);
	}
	
	private String path;
	public DirectoryHandler(String path) {
		if(path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		this.path = path;
	}
	
	protected void wrappedHandle(HttpExchange t, String[] requestPath, LinkedHashMap<String, String> query) throws IOException {
		String fullPath = Utils.getResourceDirectory() + "/" + PLUGIN_DIRECTORY + "/" + path + "/" + Utils.join(requestPath, "/");
		if(cachedFiles != null && cachedFiles.containsKey(fullPath)) {
			CachedFileInfo cached = cachedFiles.get(fullPath);
			String contentType = cached.contentType;
			azzert(contentType != null);
			byte[] data = cached.data;
			sendBytes(t, data, contentType);
			return;
		}
		File f = new File(fullPath);
		if(!f.exists()) {
			send404(t, f.getAbsolutePath());
		}
		if(f.isDirectory()) {
			File directory = f;
			if(!t.getRequestURI().getPath().endsWith("/")) {
				sendTrailingSlashRedirect(t);
				return;
			}
			f = new File(f, "index.html");
			if(!f.exists()) {
				String[] fileNames = directory.list();
				sendText(t, Utils.join(fileNames, "\n"));
				return;
			}
		}

		DataInputStream in = new DataInputStream(new FileInputStream(f));
		byte[] data = new byte[(int) f.length()];
		in.readFully(data);
		
		String fileName = f.getName();
		String contentType = null;
		String html = null;
		if(fileName.endsWith(".md")) {
			html = "<html>\n<body>\n" + mp.markdown(new String(data)) + "</body>\n</html>\n";
		} else {
			contentType = mimes.getContentType(fileName);
			if(contentType.equals("text/html")) {
				html = new String(data);
			}
		}
		if(html != null) {
			if(headInjectCode != null) {
				int closingHeadIndex = html.indexOf("</head>");
				if(closingHeadIndex < 0) {
					// If there is no <head>...</head> tag, we conjure up one of our own.
					int closingHtmlIndex = html.indexOf("</html>");
					html = html.substring(0, closingHtmlIndex) + "<head>\n</head>\n" + html.substring(closingHtmlIndex);
					closingHeadIndex = html.indexOf("</head>");
				}
				html = html.substring(0, closingHeadIndex) + "\n" + headInjectCode + "\n" + html.substring(closingHeadIndex);
			}
			contentType = "text/html";
			data = html.getBytes();
		} else {
			azzert(contentType != null);
		}
		
		if(cachedFiles != null) {
			CachedFileInfo cached = new CachedFileInfo();
			cached.contentType = contentType;
			cached.data = data;
			cachedFiles.put(fullPath, cached);
		}
		sendBytes(t, data, contentType);
	}
}
