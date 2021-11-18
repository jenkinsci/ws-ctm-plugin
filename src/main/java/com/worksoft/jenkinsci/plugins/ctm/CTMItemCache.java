/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMItemCache
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.util.HttpSessionListener;
import org.kohsuke.stapler.Stapler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import java.lang.reflect.Method;
import java.util.HashMap;

@Extension
public class CTMItemCache extends HttpSessionListener {
  // Kludge alert - In order to fill the suite&bookmark list box with values from
  // CTM and to provide the user with appropriate feedback, we need to cache
  // the list box items. We wouldn't need to do this if the 'doCheck' methods were
  // provided with the CTM configuration variables so as to be able to validate them.
  // Unfortunately, does not provide their values in a consistent manner, so we
  // use this cache to remember the items from the 'doFill' methods; which we can then
  // access in the 'doCheck' methods for proper field validation and error display.
  private static HashMap<HttpSession, HashMap<String, ListBoxModel>> itemsCache =
          new HashMap<HttpSession, HashMap<String, ListBoxModel>>();

  @Override
  public void sessionCreated (HttpSessionEvent httpSessionEvent) {
    super.sessionCreated(httpSessionEvent);

  }

  
  @Override
  public void sessionDestroyed (HttpSessionEvent httpSessionEvent) {
    super.sessionDestroyed(httpSessionEvent);
    HashMap<String, ListBoxModel> curSessionCache=itemsCache.get(httpSessionEvent);
    itemsCache.remove(httpSessionEvent);
  }

  public static ListBoxModel updateItemsCache (String fieldName, ListBoxModel items) {
    ListBoxModel prevVal = null;
    HttpServletRequest httpRequest = Stapler.getCurrentRequest();

    // Protect against non-UI related calls to this method
    if (httpRequest != null) {
      HttpSession session = httpRequest.getSession();
      String sessionId = session.getId();
      synchronized (itemsCache) {
        HashMap<String, ListBoxModel> sessionCache = itemsCache.get(session);
        if (sessionCache == null) {
          itemsCache.put(session, sessionCache = new HashMap<String, ListBoxModel>());
        }

        prevVal = getCachedItems(fieldName);
        sessionCache.put(fieldName, items);
        //System.out.println("Updated items cache for " + fieldName + "=" + items + "(prevVal=" + prevVal + ")");
      }
    }
    return prevVal;
  }

  public static ListBoxModel getCachedItems (String fieldName) {
    ListBoxModel retVal = null;
    HttpServletRequest httpRequest = Stapler.getCurrentRequest();
    // Protect against non-UI related calls to this method
    if (httpRequest != null) {
      HttpSession session = httpRequest.getSession();
      String sessionId = session.getId();
      synchronized (itemsCache) {
        HashMap<String, ListBoxModel> sessionCache = itemsCache.get(session);

        if (sessionCache != null) {
          retVal = sessionCache.get(fieldName);
        }
      }
    }
    return retVal;
  }

  public static void invalidateItemsCache () {
    HttpServletRequest httpRequest = Stapler.getCurrentRequest();
    // Protect against non-UI related calls to this method
    if (httpRequest != null) {
      HttpSession session = httpRequest.getSession();
      String sessionId = session.getId();
      itemsCache.put(session, null);
      //System.out.println("Invalidated items cache for " + sessionId);
    }
  }

  static {
    // Thread to monitor the field cache and remove entries for invalid sessions
    (new Thread() {
      public void run () {
        while (true) {
          try {
            Thread.sleep(30000);
            synchronized (itemsCache) {
              for (HttpSession key : itemsCache.keySet()) {
                try {
                  Method isValidMeth = key.getClass().getMethod("isValid");
                  if (isValidMeth != null) {
                    Boolean isValid = (Boolean) isValidMeth.invoke(key);
                    if (!isValid) {
                      itemsCache.remove(key);
                      //System.out.println("Expired field cache for " + key.getId());
                    }
                  }
                } catch (Exception ignored) {
                  itemsCache.put(key, null);
                  //System.out.println("Exception expired field cache for " + key.getId());
                }
              }
            }
          } catch (Exception ignored) {
          }
        }
      }
    }).start();
  }
}

