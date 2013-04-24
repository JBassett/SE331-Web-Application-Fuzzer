package hot.fuzzer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	
	Fuzzer() {
		// Initialization.
		lastRequestDate = null;
		minimumRequestInterval = 0;
		discoveredCookies = new HashSet<Cookie>();
		webClient = new WebClient();
		
		// Configure the web client.
		webClient.setJavaScriptEnabled(true);
	}
	
	public void close() {
		webClient.closeAllWindows();
	}
	
	public HtmlPage getPage(String url) throws IOException, MalformedURLException {
		waitForMinimumRequestTimeout();
		HtmlPage page = webClient.getPage(url);
		discoverCookies();
		return page;
	}
	
	public HtmlPage getPage(URL url) throws IOException, MalformedURLException {
		waitForMinimumRequestTimeout();
		HtmlPage page = webClient.getPage(url);
		discoverCookies();
		return page;
	}
	
	public void printReport() {
		System.out.println("==================================================");
		System.out.println("All discovered cookies:");
		for (Cookie cookie : discoveredCookies) {
			System.out.println(cookie.toString());
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
	
	/**
	 * This method will find all the links on the given page that are part of
	 * the server.
	 * 
	 * @param page
	 *            The page to search
	 * @return A list of all possible links on the server
	 */
	public List<URL> getLinks(HtmlPage page){
		List<URL> retVal = new ArrayList<URL>();
		
		for (HtmlAnchor a : page.getAnchors()) {
			try {
				if(a.getHrefAttribute().startsWith("/"))
					retVal.add(new URL(page.getUrl() + (a.getHrefAttribute().substring(1))));
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		
		return retVal;
	}

	private void discoverCookies() {
		Set<Cookie> cookies = webClient.getCookieManager().getCookies();
		if (cookies != null && cookies.size() > 0) {
			discoveredCookies.addAll(cookies);
		}
	}
	
	public static void main(String[] args) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		Fuzzer theFuzz = new Fuzzer();
		//theFuzz.getPage("http://www.google.com/");
		theFuzz.printReport();
		theFuzz.close();
	}
}
