package com.demo.core.services;

import org.apache.sling.api.SlingHttpServletRequest;

public interface ConsolidateClientlibService {
    void create(SlingHttpServletRequest slingRequest);
    void delete(SlingHttpServletRequest slingRequest);
}
