package org.wso2.mediator.custom;

import org.apache.commons.httpclient.Cookie;
//import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;

import java.net.URL;
import java.util.ArrayList;
//import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class CookieHeaderAppender extends CookieHeaderMediator {
	public void appendCookies(String endpointAddress, Map<String, ArrayList<Cookie>> cookiesMap,
	                          org.apache.synapse.MessageContext synCtx) throws Exception {

		// Load the Axis2 Message Context
		org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) synCtx).getAxis2MessageContext();

		if (endpointAddress != null && !"".equals(endpointAddress)) {
			URL endpointUrl = new URL(endpointAddress);
			String endpointUrlAuthority = endpointUrl.getAuthority();
			String endpointUrlHost = endpointUrl.getHost();
			boolean isCookiesMapModified = false;

			ArrayList<String> domains = superDomainConstructor(endpointUrlHost);

			if (domains != null && domains.size()>0) {
				for (String domain: domains) {
					// Fetch the list of cookies for the host domain and its super domains
					ArrayList<Cookie> cookieListForDomain = cookiesMap.get(domain);
					ArrayList<Cookie> expiredCookieList = new ArrayList<>();

					if (cookieListForDomain != null && cookieListForDomain.size()>0 ) {
						String transportHeaderCookiesList = "";
						for (Cookie cookie:cookieListForDomain) {
							// Check whether the cookie is expired
							// Only active cookies will be appended to the request
							if (!cookie.isExpired()) {
								// Check whether the cookie has a "path" attribute specified
								// If the cookie "path" is set, the cookie should only be sent for urls starting with
								// that "path"
								if (cookie.isPathAttributeSpecified()) {
									String cookiePath = cookie.getPath();
									String temp = endpointUrlAuthority + cookiePath;
									// Check if the endpointUrl contains the cookie path
									// Append cookie for endpointUrls containing the cookie path
									if (endpointUrl.toString().contains(temp)) {
										transportHeaderCookiesList = transportHeaderCookiesList + cookie.getName() +
												"=" + cookie.getValue() + "; ";
									}
								} else {
									transportHeaderCookiesList =
											transportHeaderCookiesList + cookie.getName() + "=" + cookie.getValue() + "; ";
								}
							} else {
								// Add expired cookies to a separate array list
								// Expired cookies will be removed from the cookiesMap
								expiredCookieList.add(cookie);
							}
						}

						if (transportHeaderCookiesList != null && !"".equals(transportHeaderCookiesList)) {
							Map transportHeaders = (Map) msgContext.getProperty(CookieHeaderMediatorConstants.TRANSPORT_HEADERS);
							if (transportHeaders != null && transportHeaders.size()>0 ) {
								transportHeaders.put("Cookie", transportHeaderCookiesList.trim());
							} else {
								transportHeaders = new TreeMap();
								transportHeaders.put("Cookie", transportHeaderCookiesList.trim());
							}

							msgContext.setProperty(CookieHeaderMediatorConstants.TRANSPORT_HEADERS, transportHeaders);
						}
					}

					if (expiredCookieList != null && expiredCookieList.size() > 0 && cookieListForDomain != null
							&& cookieListForDomain.size() > 0) {
						// Remove the expired cookies from the cookie list for the domain
						cookieListForDomain.removeAll(expiredCookieList);
						// Replace the cookiesMap with the active cookie list against the domain
						cookiesMap.put(domain, cookieListForDomain);
						// Set the isCookiesMapModified flag as true to store the modified CookiesMap to the Message Context
						isCookiesMapModified = true;
					}
				}
			}
			if (isCookiesMapModified) {
				synCtx.setProperty(CookieHeaderMediatorConstants.COOKIES_MAP, cookiesMap);
			}
		}
	}

	public ArrayList<String> superDomainConstructor(String domain) {
		ArrayList<String> domainAndSuperDomain = new ArrayList<>();
		domainAndSuperDomain.add(domain);
		if (domain != null) {
			while(domain.indexOf(".") > 0) {
				int i = domain.indexOf(".");
				domain = domain.substring(i+1);
				domainAndSuperDomain.add(domain);
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("Domains and Super Domains of the Domain: " + domainAndSuperDomain);
		}
		return domainAndSuperDomain;
	}
}
