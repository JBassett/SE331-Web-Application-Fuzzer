package hot.fuzzer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.logging.LogFactory;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

public class Fuzzer {
	private Date lastRequestDate;
	private long minimumRequestInterval;
	private WebClient webClient;
	private HashSet<Cookie> discoveredCookies;
	private HashSet<URL> discoveredURLs;

	Fuzzer() {
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

	    java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF); 
	    java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
	    
		// Initialization.
		lastRequestDate = null;
		minimumRequestInterval = 0;
		discoveredCookies = new HashSet<Cookie>();
		discoveredURLs = new HashSet<URL>();
		webClient = new WebClient();

		// Configure the web client.
		webClient.setJavaScriptEnabled(true);
	}

	public void close() {
		webClient.closeAllWindows();
	}

	public HtmlPage getPage(String url) throws IOException,
			MalformedURLException {
		return getPage(new URL(url));
	}

	public HtmlPage getPage(URL url) throws IOException, MalformedURLException {
		waitForMinimumRequestTimeout();
		HtmlPage page = webClient.getPage(url);
		discoveredURLs.add(url);
		discoverCookies();
		return page;
	}

	public void printReport() {
		System.out
				.println("==================================================");
		System.out.println("All discovered cookies:");
		for (Cookie cookie : discoveredCookies) {
			System.out.println(cookie.toString());
		}
		System.out
				.println("==================================================");
		System.out.println();
		System.out.println("All Links Discovered:");
		for (URL u : discoveredURLs) {
			System.out.println(u.toString());
		}
		System.out
				.println("==================================================");
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
		URL temp = null;
		for (HtmlAnchor a : page.getAnchors()) {
			try {
				String href = a.getHrefAttribute();
				temp = null;
				try{temp = new URL(href);}catch(MalformedURLException e){}
				if(temp != null && !temp.getProtocol().startsWith("http")){
					System.err.println("Ignoring non web protocols: " + temp);
					continue;
				}
				if(temp != null){
					if(temp.getAuthority().equalsIgnoreCase(url.getAuthority())){
						temp = cleanParameters(temp);
						if(temp != null)
							retVal.add(temp);
					}
				}else if(href.startsWith("/") && url.toString().endsWith("/")){
					temp = new URL(page.getUrl() + (href.substring(1)));
					temp = cleanParameters(temp);
					if(temp != null)
						retVal.add(temp);
				}else if(href.startsWith("#")){
				}else{
					temp = new URL(url.toString().substring(0, url.toString().lastIndexOf('/')+1) + href);
					temp = cleanParameters(temp);
					if(temp != null)
						retVal.add(temp);
				}
			} catch (MalformedURLException e) {
			}
		}

		return new ArrayList<URL>(retVal);
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

	public static void main(String[] args)
			throws FailingHttpStatusCodeException, MalformedURLException,
			IOException {
		Fuzzer theFuzz = new Fuzzer();
		//theFuzz.crawlURL(new URL("http://www.se.rit.edu/~se463/"));
		theFuzz.printReport();
		theFuzz.close();
	}
}
