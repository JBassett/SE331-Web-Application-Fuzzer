package hot.fuzzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

public class Fuzzer {
	private Date lastRequestDate;
	private long minimumRequestInterval;
	private WebClient webClient;
	private HashSet<Cookie> discoveredCookies;
	private HashSet<URL> discoveredURLs;
	private HashSet<URL> discoveredPages;
	private HashMap<URL, List<String>> discoveredPageInputs;
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
		discoveredPages = new HashSet<URL>();
		discoveredPageInputs = new HashMap<URL, List<String>>();
		discoveredForms = new HashMap<URL, List<HtmlForm>>();
		webClient = new WebClient();

		// Configure the web client.
		webClient.setJavaScriptEnabled(true);
	}

	/**
	 * Called when the fuzzer is no longer to be used.
	 */
	public void close() {
		webClient.closeAllWindows();
	}

	/**
	 * Convenience method for getPage(URL)
	 * 
	 * @param url - desired url as a string
	 * @return the page which the url corresponds to.
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public HtmlPage getPage(String url) throws IOException, MalformedURLException {
		return getPage(new URL(url));
	}

	/**
	 * Fuzzer's designated method for requesting pages via the web client. Will perform
	 * all the various housekeeping tasks related to processing a single page.
	 * 
	 * @param url - the desired url
	 * @return the page which the url corresponds to.
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public HtmlPage getPage(URL url) throws IOException, MalformedURLException {
		waitForMinimumRequestTimeout();
		HtmlPage page = webClient.getPage(url);
		parseURL(url);
		discoverCookies();
		discoverForms(page);
		return page;
	}

	/**
	 * Prints to standard out a report of this fuzzer which displays the current
	 * aggregation of the data it's collected.
	 */
	public void printReport() {
		System.out.println("==================================================");
		System.out.println("All discovered pages with inputs:");
		for (URL url : discoveredPages) {
			System.out.println("\nPage: " + url.toString());
			for (String parameter : discoveredPageInputs.get(url)) {
				System.out.println("    " + parameter);
			}
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
		System.out.println("All discovered cookies:");
		for (Cookie cookie : discoveredCookies) {
			System.out.println(cookie.toString());
		}
		System.out.println("==================================================");
		System.out.println("All links discovered:");
		for (URL u : discoveredURLs) {
			System.out.println(u.toString());
		}
		
		System.out.println("==================================================");
	}

	/**
	 * The minimum length of time before the fuzzer can make another web request.
	 * @return the interval in milliseconds
	 */
	public long getMinimumRequestInterval() {
		return minimumRequestInterval;
	}

	/**
	 * Set the minimum length of time before the fuzzer can make another web request.
	 * @param minimumRequestInterval - the new length of time in milliseconds
	 */
	public void setMinimumRequestInterval(long minimumRequestInterval) {
		this.minimumRequestInterval = minimumRequestInterval;
	}

	/**
	 * Recursively crawls a given url for linked pages, ignoring previously seen urls.
	 * @param url - the url which to crawl.
	 */
	public void crawlURL(URL url) {
		List<URL> urls = getLinks(url);
		for (URL u : urls) {
			if (!discoveredURLs.contains(u) && u.getHost().equals(url.getHost()))
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
		} catch (Exception e) {
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
	
	/**
	 * Removes the query and fragment components of a url.
	 * 
	 * @param url - the url to clean
	 * @return a url with the query and fragment removed.
	 */
	private URL cleanParameters(URL url){
		try {
			// TODO: What happens if the url has a port?
			return new URL(url.getProtocol() + "://" +url.getHost() + url.getPath());
		} catch (MalformedURLException e) {
			return null;
		}
	}

	/**
	 * Method which ensures that the fuzzer does not make requests at a faster rate
	 * than specified using the minimum request interval.
	 */
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
	
	/**
	 * Keeps track of all the URLs which have been requested by the fuzzer, as well as
	 * keeping track of the base url (sans query). GET parameters are recorded as inputs.
	 * 
	 * @param url - the url which is to be processed.
	 * @throws MalformedURLException
	 */
	private void parseURL(URL url) throws MalformedURLException {
		// Keep track that we've seen this URL being asked for.
		discoveredURLs.add(url);
		
		// Keep track of the base URL of the page (sans query)
		URL parsedURL = new URL(url.toString().split("\\?")[0].split("#")[0]);
		discoveredPages.add(parsedURL);
		
		// Discover the inputs for the page.
	    List<String> pageParameters = new ArrayList<String>();
		String query = url.getQuery();
		if (query != null) {
			String[] parameterPairs = query.split("&");
		    for (String pair : parameterPairs) {
		        pageParameters.add(pair.substring(0, pair.indexOf("=")));
		    }
		}
	    
	    discoveredPageInputs.put(parsedURL, pageParameters);
	}
	
	/**
	 * Grabs the current cookies from the web client and keeps track of them.
	 */
	private void discoverCookies() {
		Set<Cookie> cookies = webClient.getCookieManager().getCookies();
		if (cookies != null && cookies.size() > 0) {
			discoveredCookies.addAll(cookies);
		}
	}
	
	/**
	 * Grabs the forms which are available on the given page.
	 * 
	 * @param page - the page which to inspect for forms.
	 */
	private void discoverForms(HtmlPage page) {
		List<HtmlForm> forms = page.getForms();
		discoveredForms.put(page.getUrl(), forms);
	}
	
	/**
	 * Given an external list of common unlinked pages which may be accessible
	 * from same path of a given url, determines if those pages exist.
	 * 
	 * @param baseUrl - the url with the path to guess the pages at.
	 * @return
	 */
	public List<HtmlPage> guessPages(URL baseUrl){
		ArrayList<HtmlPage> retVal = new ArrayList<HtmlPage>();
		Scanner s = null;
		try {
			s = new Scanner(new File("PageGuessing.txt"));
			while(s.hasNextLine()){
				try{
					retVal.add(getPage(addToURL(baseUrl, s.nextLine())));
				} catch (IOException e) {
					
				} catch (FailingHttpStatusCodeException e) {
					// Ignore 404, file not found because it was a guess.
					if (e.getStatusCode() != 404) {
						throw e;
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(s!=null) s.close();
		}
		return retVal;
	}
	
	public HtmlPage logIntoDVWA() throws MalformedURLException, IOException{
		String host = "http://10.211.55.3/";
		String path = "dvwa/login.php";
		
		HtmlPage p = getPage(host+path);
		
		((HtmlInput) p.getElementByName("username")).setValueAttribute("admin");
		((HtmlInput) p.getElementByName("password")).setValueAttribute("password");
		
		return getPage(p.getElementByName("Login").click().getUrl());
	}

	public static void main(String[] args) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		Fuzzer theFuzz = new Fuzzer();
		
		HtmlPage p = theFuzz.logIntoDVWA();
		
		p = theFuzz.getPage("http://10.211.55.3/dvwa/index.php");
		
		theFuzz.crawlURL(new URL("http://10.211.55.3/dvwa/index.php"));
		
		theFuzz.printReport();
		theFuzz.close();
	}
}
