package org.wso2.mediator.custom;

import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import java.util.ArrayList;
//import java.net.URL;
//import java.util.Date;
//import java.util.HashMap;
//import java.text.SimpleDateFormat;
import java.util.Map;
import org.apache.commons.httpclient.Cookie;

public class CookieHeaderMediator extends AbstractMediator {
	protected static final Log log = LogFactory.getLog(CookieHeaderMediator.class);

	public boolean mediate(MessageContext synCtx) {
		try {
			if (log.isDebugEnabled()) {
				log.debug("Executing the CookieHeaderMediator...");
			}

			// Load the Axis2 Message Context
			org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) synCtx).getAxis2MessageContext();

			// Extracting the excess transport headers and transport headers from the Axis2 Message Context
			Map excessTransportHeaders = (Map) msgContext.getProperty(NhttpConstants.EXCESS_TRANSPORT_HEADERS);
			Map transportHeaders = (Map) msgContext.getProperty(CookieHeaderMediatorConstants.TRANSPORT_HEADERS);

			// Extracting the cookie action set in the property
			String cookieAction = (String) synCtx.getProperty(CookieHeaderMediatorConstants.COOKIE_ACTION);
			if (log.isDebugEnabled()) {
				log.debug("Cookie Action configured is: " + cookieAction);
			}

			// Execute this segment if the "cookie-action" is "get-transport"
			if (cookieAction != null &&  !"".equals(cookieAction)
					&& cookieAction.equals(CookieHeaderMediatorConstants.GET_COOKIE_ACTION)) {
				// Retrieve the "domain" set from the mediation sequence
				String domain = (String) synCtx.getProperty(CookieHeaderMediatorConstants.DOMAIN);
				if (log.isDebugEnabled()) {
					log.debug("Domain set to the context: " + domain);
				}

				// Loading the cookies map from the message context
				Map<String, ArrayList<Cookie>> previousCookiesMap =
						(Map<String, ArrayList<Cookie>>) (synCtx.getProperty(CookieHeaderMediatorConstants.COOKIES_MAP));

				ArrayList<String> excessTransportHeaderCookiesList = null;
				String transportHeaderCookie = null;
				ArrayList<String> headerCookiesList = new ArrayList<>();

				if (excessTransportHeaders != null && excessTransportHeaders.size() > 0
						&& excessTransportHeaders.containsKey("Set-Cookie")) {
					// A sample 'Set-Cookie' header is the following,
					// AWSALBCORS=w/v...; Expires=Sun, 08 Aug 2021 03:50:21 GMT; Path=/; SameSite=None; Secure
					excessTransportHeaderCookiesList = (ArrayList<String>) excessTransportHeaders.get("Set-Cookie");
					if (log.isDebugEnabled()) {
						log.debug("Excess Transport Header Cookies: " + excessTransportHeaderCookiesList.toString());
					}
				}

				if (transportHeaders != null && transportHeaders.size() > 0
						&& transportHeaders.containsKey("Set-Cookie")) {
					transportHeaderCookie = (String) transportHeaders.get("Set-Cookie");
					if (log.isDebugEnabled()) {
						log.debug("Transport Header Cookie: " + transportHeaderCookie);
					}
				}

				if (excessTransportHeaderCookiesList != null) {
					headerCookiesList.addAll(excessTransportHeaderCookiesList);
				}

				if (transportHeaderCookie != null) {
					headerCookiesList.add(transportHeaderCookie);
				}

				CookieHeaderRetriever cookieHeaderRetriever = new CookieHeaderRetriever();
				Map<String, ArrayList<Cookie>> currentCookiesMap =
						cookieHeaderRetriever.extractCookies(headerCookiesList, domain, synCtx);

				Map<String, ArrayList<Cookie>> cookiesMap;

				if (previousCookiesMap != null && previousCookiesMap.size() > 0) {
					cookiesMap = cookieHeaderRetriever.mergeCookiesMap(previousCookiesMap, currentCookiesMap);
				} else {
					cookiesMap = currentCookiesMap;
				}

				if (cookiesMap != null && cookiesMap.size() > 0) {
					if (log.isDebugEnabled()) {
						log.debug("Printing the cookiesMap...");
					}
					for (Map.Entry<String, ArrayList<Cookie>> entry : cookiesMap.entrySet()) {
						for (Cookie cookie:entry.getValue()) {
							if (log.isDebugEnabled()) {
								log.debug(entry.getKey() + ":" + cookie.getName() + "=" + cookie.getValue());
							}
						}
					}
					synCtx.setProperty(CookieHeaderMediatorConstants.COOKIES_MAP, cookiesMap);
				}
			}

			if (cookieAction != null && !"".equals(cookieAction)
					&& cookieAction.equals(CookieHeaderMediatorConstants.SET_COOKIE_ACTION)) {
				// Fetch the "To" header from the Message Context
				// Extract the "domain" from the "To" header
				String endpointAddress = msgContext.getOptions().getTo().getAddress();
				// Loading the cookies map from the message context
				Map<String, ArrayList<Cookie>> cookiesMap =
						(Map<String, ArrayList<Cookie>>) synCtx.getProperty(CookieHeaderMediatorConstants.COOKIES_MAP);

				// Fetch all the cookies based on the "domain"
				// Filter out all expired cookies
				// Filter the cookies based on the cookie "path"
				// Set the "active" cookies applicable for the "domain" and "path" to transport header
				CookieHeaderAppender cookieHeaderAppender = new CookieHeaderAppender();
				cookieHeaderAppender.appendCookies(endpointAddress, cookiesMap, synCtx);
			}

		} catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Exception: " + e);
			}
			handleException("Exception", e, synCtx);
		}
		return true;
	}
}
