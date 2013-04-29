package hot.fuzzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.logging.LogFactory;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

public class Fuzzer {
	private Date lastRequestDate;
	private long minimumRequestInterval;
	private WebClient webClient;
	private HashSet<Cookie> discoveredCookies;
	private HashSet<URL> discoveredURLs;
	private HashMap<URL, List<HtmlForm>> discoveredForms;
	
	Fuzzer() {
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

	    java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF); 
	    java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
	    
		// Initialization.
		lastRequestDate = null;
		minimumRequestInterval = 0;
		discoveredCookies = new HashSet<Cookie>();
		discoveredURLs = new HashSet<URL>();
		discoveredForms = new HashMap<URL, List<HtmlForm>>();
		webClient = new WebClient();

		// Configure the web client.
		webClient.setJavaScriptEnabled(true);
	}

	public void close() {
		webClient.closeAllWindows();
	}

	public HtmlPage getPage(String url) throws IOException, MalformedURLException {
		return getPage(new URL(url));
	}

	public HtmlPage getPage(URL url) throws IOException, MalformedURLException {
		waitForMinimumRequestTimeout();
		HtmlPage page = webClient.getPage(url);
		discoveredURLs.add(url);
		discoverCookies();
		discoverForms(page);
		return page;
	}

	public void printReport() {
		System.out
				.println("==================================================");
		System.out.println("All discovered cookies:");
		for (Cookie cookie : discoveredCookies) {
			System.out.println(cookie.toString());
		}
		System.out.println("==================================================");
		System.out.println();
		System.out.println("All Links Discovered:");
		for (URL u : discoveredURLs) {
			System.out.println(u.toString());
		}
		System.out.println("==================================================");
		System.out.println("All discovered forms:");
		for (Map.Entry<URL, List<HtmlForm>> entry : discoveredForms.entrySet()) {
			System.out.println("\nPage: " + entry.getKey());
			for (HtmlForm form : entry.getValue()) {
				System.out.println("    " + form.toString());
			}
		}
		System.out.println("==================================================");
	}

	public long getMinimumRequestInterval() {
		return minimumRequestInterval;
	}

	public void setMinimumRequestInterval(long minimumRequestInterval) {
		this.minimumRequestInterval = minimumRequestInterval;
	}

	private void waitForMinimumRequestTimeout() {
		if (lastRequestDate != null) {
			long timeSince = (new Date()).getTime() - lastRequestDate.getTime();
			if (timeSince < minimumRequestInterval) {
				try {
					Thread.sleep(minimumRequestInterval - timeSince);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		lastRequestDate = new Date();
	}

	public void crawlURL(URL url) {
		List<URL> urls = getLinks(url);
		for (URL u : urls) {
			if (!discoveredURLs.contains(u))
				crawlURL(u);
		}
	}

	/**
	 * This method will find all the links on the given page that are part of
	 * the server.
	 * 
	 * @param page
	 *            The page to search
	 * @return A list of all possible links on the server
	 */
	public List<URL> getLinks(URL url) {
		HashSet<URL> retVal = new HashSet<URL>();
		HtmlPage page = null;
		try {
			URI uri = new URI(url.toString());
			uri = uri.normalize();
			url = uri.toURL();
			page = getPage(url);
		} catch (IOException | ClassCastException | FailingHttpStatusCodeException | URISyntaxException e1) {
			System.err.println("BAD URL: " + url);
			return new ArrayList<URL>();
		}
		for (HtmlAnchor a : page.getAnchors()) {
			URL pageUrl = page.getUrl();
			String href = a.getHrefAttribute();
			URL ret = addToURL(pageUrl, href);
			if(ret != null)
				retVal.add(ret);
		}

		return new ArrayList<URL>(retVal);
	}
	
	private URL addToURL(URL pageUrl, String ref){
		URL temp = null;
		try{
			try{temp = new URL(ref);}catch(MalformedURLException e){}

			if(temp != null){
				if(temp.getAuthority().equalsIgnoreCase(pageUrl.getAuthority())){
					temp = cleanParameters(temp);
				}
			}else if(ref.startsWith("/") && pageUrl.toString().endsWith("/")){
				temp = new URL(pageUrl + (ref.substring(1)));
				temp = cleanParameters(temp);
			}else if(ref.startsWith("#")){
			}else{
				temp = new URL(pageUrl.toString().substring(0, pageUrl.toString().lastIndexOf('/')+1) + ref);
				temp = cleanParameters(temp);
			}
		}catch(MalformedURLException e){
			
		}
		return temp;
	}
	
	private URL cleanParameters(URL url){
		try {
			return new URL(url.getProtocol() + "://" +url.getHost() + url.getPath());
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private void discoverCookies() {
		Set<Cookie> cookies = webClient.getCookieManager().getCookies();
		if (cookies != null && cookies.size() > 0) {
			discoveredCookies.addAll(cookies);
		}
	}
	
	private void discoverForms(HtmlPage page) {
		List<HtmlForm> forms = page.getForms();
		discoveredForms.put(page.getUrl(), forms);
	}
	
	public List<HtmlPage> guessPages(URL baseUrl){
		ArrayList<HtmlPage> retVal = new ArrayList<HtmlPage>();
		Scanner s = null;
		try {
			s = new Scanner(new File("PageGuessing.txt"));
			while(s.hasNextLine()){
				try{
					retVal.add(getPage(addToURL(baseUrl, s.nextLine())));
				}catch(IOException e){}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(s!=null) s.close();
		}
		return retVal;
	}
	
	public static void main(String[] args) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		Fuzzer theFuzz = new Fuzzer();
		HtmlPage p = theFuzz.getPage("http://www.google.com/");
		
		theFuzz.guessPages(p.getUrl());
		
		theFuzz.printReport();
		theFuzz.close();
	}
}
