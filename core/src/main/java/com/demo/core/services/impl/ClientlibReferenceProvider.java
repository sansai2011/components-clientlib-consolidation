package com.demo.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.PageManagerFactory;
import com.day.cq.wcm.api.reference.ReferenceProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component(
        immediate = true, service = {ReferenceProvider.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "= For providing page references",
                Constants.SERVICE_VENDOR + "= Demo"
        })
public class ClientlibReferenceProvider implements ReferenceProvider {
    @ObjectClassDefinition(name = "wcm.io Context-Aware Configuration Reference Provider",
            description = "Allows to resolve references from resources to their Context-Aware configurations, for example during page activation.")
    @interface Config {

        @AttributeDefinition(name = "Enabled",
                description = "Enable this reference provider.")
        boolean enabled() default true;
    }

    private boolean enabled;

    @Reference
    private PageManagerFactory pageManagerFactory;

    @Activate
    protected void activate(Config config) {
        enabled = config.enabled();
    }

    @Deactivate
    protected void deactivate() {
        enabled = false;
    }

    private final String CLIENTLIB_ROOT_FOLDER = "/etc/clientlibs/demo/clientlib-";

    @Override
    public List<com.day.cq.wcm.api.reference.Reference> findReferences(Resource resource) {
        ResourceResolver resolver = resource.getResourceResolver();
        if (!enabled) {
            return Collections.emptyList();
        }

        PageManager pageManager = pageManagerFactory.getPageManager(resource.getResourceResolver());
        if (pageManager == null) {
            throw new RuntimeException("No page manager.");
        }

        Page contextPage = pageManager.getContainingPage(resource);
        if (contextPage == null) {
            return Collections.emptyList();
        }

        List<com.day.cq.wcm.api.reference.Reference> references = new ArrayList<>();
        try {
            String clientLibPath = CLIENTLIB_ROOT_FOLDER + "/" + contextPage.getAbsoluteParent(1)
                    .getName() + "/" + "clientlib-" + contextPage.getName();
            Resource clientLibResource = resolver.getResource(clientLibPath);
            Resource cssResource = resolver.getResource(clientLibPath + "/css.txt");
            Resource jsLibResource = resolver.getResource(clientLibPath + "/js.txt");

            if (clientLibResource != null && !clientLibResource.getPath().equals(resource.getPath())) {
                references.add(getReference(clientLibResource, contextPage));
                references.add(getReference(cssResource, contextPage));
                references.add(getReference(jsLibResource, contextPage));
            }
        } catch (NullPointerException e) {
            log.error("Null Pointer Exception at {}", contextPage);
        }
        log.debug("Found {} references for resource {}", references.size(), resource.getPath());
        return references;
    }

    private com.day.cq.wcm.api.reference.Reference getReference(Resource clientLibResource, Page page) {
        return new com.day.cq.wcm.api.reference.Reference("ClientLibraryFolder",
                clientLibResource.getName(),
                clientLibResource,
                getLastModifiedTimeOfResource(page));
    }

    private long getLastModifiedTimeOfResource(Page page) {
        final Calendar mod = page.getLastModified();
        long lastModified = mod != null ? mod.getTimeInMillis() : -1;
        return lastModified;
    }
}
