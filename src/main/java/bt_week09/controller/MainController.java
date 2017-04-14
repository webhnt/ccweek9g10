package bt_week09.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import bt_week09.model.Post;
import bt_week09.service.PostService;
import bt_week09.storge.StorageFileNotFoundException;
import bt_week09.storge.StorageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;

@Controller
public class MainController {

	private final StorageService storageService;
	private static final String APPLICATION_NAME = "Drive API Java Quickstart";
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/drive-java-quickstart");
	private static FileDataStoreFactory DATA_STORE_FACTORY;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static HttpTransport HTTP_TRANSPORT;
	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);
	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	@Autowired
	public MainController(StorageService storageService) {
		this.storageService = storageService;
	}

	@Autowired
	private PostService postService;

	@GetMapping("/")
	public String home(HttpServletRequest request) {
		request.setAttribute("mode", "MODE_HOME");
		return "index";
	}

	@GetMapping("/all-post")
	public String allPost(HttpServletRequest request) {
		request.setAttribute("lstPost", postService.findAll());
		request.setAttribute("mode", "MODE_ALL");
		return "index";
	}

	@GetMapping("/add-post")
	public String addPost(HttpServletRequest request) {
		request.setAttribute("mode", "MODE_ADD");
		return "index";
	}

	public static Credential authorize() throws Exception {
		// Load client secrets.
		InputStream in = MainController.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	public static Drive getDriveService() throws Exception {
		Credential credential = authorize();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	}

	@PostMapping("/save-post")
	public String savePost(@RequestParam("file") MultipartFile file, @ModelAttribute Post post,
			BindingResult bindingResult, HttpServletRequest request) {
		try {
			if (file != null) {
				storageService.store(file);
				// google drive
				Drive service = getDriveService();
				File fileMetadata = new File();
				fileMetadata.setName(file.getOriginalFilename());
				java.io.File filePath = new java.io.File("upload-dir/" + file.getOriginalFilename());
				FileContent mediaContent = new FileContent(file.getContentType(), filePath);
				File f = service.files().create(fileMetadata, mediaContent).setFields("id").execute();

				post.setFile("https://drive.google.com/open?id=" + f.getId());
			}
		} catch (Exception e) {
			System.out.println("Save file error: " + e);
			Post _postOld = postService.findPost(post.getId());
			if (_postOld != null) {
				post.setFile(_postOld.getFile());
			}
		}
		postService.save(post);
		request.setAttribute("lstPost", postService.findAll());
		request.setAttribute("mode", "MODE_ALL");
		return "redirect:/all-post";

	}

	@GetMapping("/update-post")
	public String updatePost(@RequestParam int id, HttpServletRequest request) {
		request.setAttribute("post", postService.findPost(id));
		request.setAttribute("mode", "MODE_UPDATE");
		return "index";
	}

	@GetMapping("/delete-post")
	public String deletePost(@RequestParam int id, HttpServletRequest request) {
		try {
			Post post = postService.findPost(id);
			deleteFile(getDriveService(), post.getFile().split("=")[1]);
		} catch (Exception e) {
		}
		postService.delete(id);
		request.setAttribute("lstPost", postService.findAll());
		request.setAttribute("mode", "MODE_ALL");
		return "redirect:/all-post";
	}

	private static void deleteFile(Drive service, String fileId) {
		try {
			service.files().delete(fileId).execute();
		} catch (IOException e) {
			System.out.println("Delete file error: " + e);
		}
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}
}
