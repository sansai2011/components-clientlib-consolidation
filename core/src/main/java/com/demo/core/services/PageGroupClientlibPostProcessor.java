package com.demo.core.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;

@Slf4j
@Component(service = SlingPostProcessor.class, immediate = true)
public class PageGroupClientlibPostProcessor implements SlingPostProcessor {

    @Reference
    private transient ConsolidateClientlibService consolidateClientlibService;

    @Override
    public void process(SlingHttpServletRequest request, List<Modification> list) {
        String operation = String.valueOf(request.getRequestParameterMap().
                getValue(":operation"));
        if (operation.equalsIgnoreCase("delete")) {
            consolidateClientlibService.delete(request);
        }
        if (request.getRequestParameterMap().
                getValue("./sling:resourceType") != null) {
            consolidateClientlibService.create(request);
        }
    }
}
