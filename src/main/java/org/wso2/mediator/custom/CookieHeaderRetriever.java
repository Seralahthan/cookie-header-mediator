package org.wso2.mediator.custom;

import org.apache.synapse.MessageContext;
//import org.apache.synapse.core.axis2.Axis2MessageContext;
//import org.apache.synapse.mediators.AbstractMediator;
//import org.apache.synapse.transport.nhttp.NhttpConstants;

import java.util.*;
import java.text.SimpleDateFormat;
import org.apache.commons.httpclient.Cookie;

public class CookieHeaderRetriever extends CookieHeaderMediator {
	public Map<String, ArrayList<Cookie>> extractCookies(ArrayList<String> headerCookiesList, String domain,
	                                                     MessageContext synCtx) {
		String cookieName, cookieValue, cookiePath;
		String cookieDomain = "";
		Date cookieExpiry;
		boolean cookieIsSecure;

		Map <String, ArrayList<Cookie>> cookiesMap = new HashMap<>();
		ArrayList<Cookie> cookieArrayList;

		for (String cookieString: headerCookiesList) {
			Cookie cookie = new Cookie();
			String[] cookieAttributes = cookieString.split(";");
			if (cookieAttributes.length > 0) {
				cookieName = extractCookieNameAndValue(cookieAttributes)[0];
				cookieValue = extractCookieNameAndValue(cookieAttributes)[1];
				if (cookieName != null && !"".equals(cookieName) && cookieValue != null && !"".equals(cookieValue)) {
					cookie.setName(cookieName);
					cookie.setValue(cookieValue);

					cookiePath = extractCookiePath(cookieAttributes);
					if (cookiePath != null && !"".equals(cookiePath)) {
						cookie.setPath(cookiePath);
						cookie.setPathAttributeSpecified(true);
					}

					cookieDomain = extractCookieDomain(cookieAttributes);
					if (cookieDomain != null && !"".equals(cookieDomain)) {
						cookie.setDomain(cookieDomain);
					}

					cookieIsSecure = cookieIsSecure(cookieAttributes);
					cookie.setSecure(cookieIsSecure);

					try {
						cookieExpiry = extractCookieExpiryTime(cookieAttributes);
						cookie.setExpiryDate(cookieExpiry);
					} catch (Exception e) {
						if (log.isDebugEnabled()) {
							log.debug("Exception: " + e);
						}
						handleException("Exception", e, synCtx);
					}
				}
			}

			// Checking if the cookie is set
			// The default Cookie cookie = new Cookie(); creates a cookie object with "noname" as the cookie name
			if (!"noname".equals(cookie.getName())) {
				if (cookieDomain != null && !"".equals(cookieDomain)) {
					if (cookiesMap.containsKey(cookieDomain)) {
						cookieArrayList = cookiesMap.get(cookieDomain);
					} else {
						cookieArrayList = new ArrayList<>();
					}
					cookieArrayList.add(cookie);
					cookiesMap.put(cookieDomain, cookieArrayList);
				} else {
					if (domain != null && !"".equals(domain)) {
						if (cookiesMap.containsKey(domain)) {
							cookieArrayList = cookiesMap.get(domain);
						} else {
							cookieArrayList = new ArrayList<>();
						}
						cookieArrayList.add(cookie);
						cookiesMap.put(domain, cookieArrayList);
					}
				}
			}
		}
		return cookiesMap;
	}

	public String[] extractCookieNameAndValue(String[] cookieAttributes) {
		String cookieName, cookieValue;
		String[] cookieNameAndValue = new String[2];
		int i = cookieAttributes[0].indexOf("=");
		if (i >= 0) {
			cookieName = cookieAttributes[0].substring(0, i).trim();
			cookieValue = cookieAttributes[0].substring(i+1).trim();
			cookieNameAndValue[0] = cookieName;
			cookieNameAndValue[1] = cookieValue;
		}
		return cookieNameAndValue;
	}

	public String extractCookiePath(String[] cookieAttributes) {
		return extractCookieAttribute("path", "Path", cookieAttributes);
	}

	public String extractCookieDomain(String[] cookieAttributes) {
		return extractCookieAttribute("domain", "Domain", cookieAttributes);
	}

	public boolean cookieIsSecure(String[] cookieAttributes) {
		return Arrays.toString(cookieAttributes).contains("Secure") || Arrays.toString(cookieAttributes).contains("secure");
	}

	public Date extractCookieExpiryTime(String[] cookieAttributes) throws Exception {
		Date date = new Date();
		long maxAge;
		SimpleDateFormat formatter1 = new SimpleDateFormat(CookieHeaderMediatorConstants.DATE_PATTERN_1);
		SimpleDateFormat formatter2 = new SimpleDateFormat(CookieHeaderMediatorConstants.DATE_PATTERN_2);
		String expiresStr = extractCookieAttribute("Expires", "expires", cookieAttributes);
		String maxAgeStr = extractCookieAttribute("Max-Age", "max-age", cookieAttributes);
		if (expiresStr != null && !"".equals(expiresStr) && maxAgeStr != null && !"".equals(maxAgeStr)) {
			maxAge = Long.parseLong(maxAgeStr);
			if (maxAge > 0) {
				date = new Date(System.currentTimeMillis() + (maxAge*1000));
			}
		} else {
			if (expiresStr != null && !"".equals(expiresStr)) {
				if (expiresStr.contains("-")) {
					date = formatter1.parse(expiresStr);
				} else {
					date = formatter2.parse(expiresStr);
				}
			}

			if (maxAgeStr != null && !"".equals(maxAgeStr)) {
				maxAge = Long.parseLong(maxAgeStr);
				if (maxAge > 0) {
					date = new Date(System.currentTimeMillis() + (maxAge*1000));
				}
			}

			if ((maxAgeStr == null || "".equals(maxAgeStr)) && (expiresStr == null || "".equals(expiresStr))) {
				date = new Date(System.currentTimeMillis() + (3600*1000));
			}
		}
		return date;
	}

	public Map<String, ArrayList<Cookie>> mergeCookiesMap(Map<String, ArrayList<Cookie>> previousCookiesMap,
	                                                      Map<String, ArrayList<Cookie>> currentCookiesMap ) {
		// create a new cookiesMap and populate it with previousCookiesMap entries
		Map<String, ArrayList<Cookie>> cookiesMap = new HashMap<>(previousCookiesMap);

		for (Map.Entry<String, ArrayList<Cookie>> entry : currentCookiesMap.entrySet()) {
			/*
			* Check whether the cookie domains in the currentCookiesMap already exist in the previousCookiesMap
			* If previousCookiesMap contains a cookie domain which exists in the currentCookiesMap
			* Insert the new cookies of currentCookiesList to the previousCookiesList against the existing cookie domain
			* Replace the existing cookies in the previousCookiesList with the new cookies from the currentCookiesList
			* */
			if (previousCookiesMap.containsKey(entry.getKey())) {
				ArrayList<Cookie> previousCookiesList = previousCookiesMap.get(entry.getKey());
				ArrayList<Cookie> currentCookiesList = entry.getValue();
				for (Cookie currentCookie:currentCookiesList) {
					boolean currentCookieAlreadyExists = false;
					int index = 0;
					for (int i=0; i < previousCookiesList.size(); i++) {
						if (currentCookie.getName().equals(previousCookiesList.get(i).getName())) {
							currentCookieAlreadyExists = true;
							index = i;
						}
					}
					if (currentCookieAlreadyExists) {
						previousCookiesList.set(index, currentCookie);
					} else {
						previousCookiesList.add(currentCookie);
					}
 				}
				// Update the cookiesMap with the modified previousCookiesList for already existing cookie domains
				cookiesMap.put(entry.getKey(), previousCookiesList);
			} else {
				/*
				 * previousCookiesMap doesn't have cookie domains which exists in the currentCookiesMap
				 * Insert the current cookies list to the cookiesMap against the cookie domain
				 * */
				cookiesMap.put(entry.getKey(), entry.getValue());
			}
		}
		return cookiesMap;
	}

	public String extractCookieAttribute(String cookieAttributeName1, String cookieAttributeName2, String[] cookieAttributes) {
		String cookieAttributeValue = "";
		for (String cookieAttribute: cookieAttributes) {
			if (cookieAttribute.contains(cookieAttributeName1) || cookieAttribute.contains(cookieAttributeName2)) {
				cookieAttributeValue = cookieAttribute.split("=")[1].trim();
			}
		}
		return cookieAttributeValue;
	}
}
