package com.demo.core.services.impl;


import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.demo.core.services.ConsolidateClientlibService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.*;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Component(
        immediate = true, service = {ConsolidateClientlibService.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "= For consolidating all component level clientlibs",
                Constants.SERVICE_VENDOR + "= Demo"
        })
public class ConsolidateClientlibServiceImpl implements ConsolidateClientlibService {

    @Reference
    private ResourceResolverFactory resolverFactory;

    private final String TYPE_CQ_CLIENTLIB_FOLDER = "cq:ClientLibraryFolder";
    private final String CLIENTLIB_FOLDER = "clientlibs";
    private final String CLIENTLIB_ROOT_FOLDER = "/etc/clientlibs/demo/clientlib-";
    private final String CLIENTLIB_CATEGORIES = "categories";
    private final String CLIENTLIB_EMBED = "embed";
    private static final String SLASH = "/";
    private static final String JS_TXT_FILE = "js.txt";
    private static final String CSS_TXT_FILE = "css.txt";

    private ResourceResolver getServiceResourceResolver() throws LoginException {
        return resolverFactory.getServiceResourceResolver(Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE,
                (Object) "consolidate-clientlibs-service"));
    }

    @Override
    public void create(SlingHttpServletRequest slingRequest) {
        ResourceResolver resourceResolver = null;
        Page currentPage = getCurrentPage(slingRequest);
        try {
            resourceResolver = getServiceResourceResolver();

            Set<String> categories = new HashSet<>();
            categories.add("demo." + currentPage.getName());

            Map<String, Object> defaultProps = new HashMap<>();
            defaultProps.put(JcrConstants.JCR_PRIMARYTYPE, TYPE_CQ_CLIENTLIB_FOLDER);
            defaultProps.put(CLIENTLIB_CATEGORIES, categories.toArray());

            String resourceType = String.valueOf(slingRequest.getRequestParameterMap().
                    getValue("./sling:resourceType"));
            String componentName = resourceType.substring(resourceType.lastIndexOf(SLASH) + 1);
            Resource source = ResourceUtil.getOrCreateResource(resourceResolver, CLIENTLIB_ROOT_FOLDER + currentPage.getName(),
                    defaultProps, null, false);

            Map<String, Object> props = new HashMap<>();
            props.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE);
            props.put(JcrConstants.JCR_DATA, InputStream.class);

            if (Objects.isNull(source.getChild(JS_TXT_FILE))) {
                Resource jsResource = resourceResolver.create(source, JS_TXT_FILE, Collections.singletonMap(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE));
                resourceResolver.create(jsResource, JcrConstants.JCR_CONTENT, props);
            }
            if (Objects.isNull(source.getChild(CSS_TXT_FILE))) {
                Resource cssResource = resourceResolver.create(source, CSS_TXT_FILE, Collections.singletonMap(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE));
                resourceResolver.create(cssResource, JcrConstants.JCR_CONTENT, props);
            }

            Resource sourceComponent = resourceResolver.getResource(resourceType + SLASH + CLIENTLIB_FOLDER);

            if (Objects.nonNull(sourceComponent)) {
                ModifiableValueMap modValueMap = source.adaptTo(ModifiableValueMap.class);
                if (modValueMap == null) {
                    log.error("No write access: Unable to store resource data to {}", source.getPath() + ".");
                }
                if (modValueMap != null) {
                    int count = 1;
                    Set<String> sourceProps = getProps(sourceComponent, CLIENTLIB_CATEGORIES);
                    if (!modValueMap.containsKey(CLIENTLIB_EMBED)) {
                        modValueMap.putIfAbsent(CLIENTLIB_EMBED, sourceProps.toArray());
                        modValueMap.putIfAbsent(componentName, count);
                    } else {
                        Set<String> destProps = getProps(source, CLIENTLIB_EMBED);
                        modValueMap.replace(CLIENTLIB_EMBED, SetUtils.union(sourceProps, destProps).toArray());
                        if (modValueMap.containsKey(componentName)) {
                            Long getCount = (Long) modValueMap.get(componentName);
                            modValueMap.replace(componentName, getCount + 1);
                        } else {
                            modValueMap.putIfAbsent(componentName, count);
                        }
                    }
                }
            }
            resourceResolver.commit();
        } catch (LoginException e) {
            log.error("Unable to Login", e);
        } catch (PersistenceException e) {
            log.error("Failed to create counter folder ", e.getMessage(), e);
        } catch (RepositoryException e) {
            log.error("Failed to retrieve properties", e);
        } catch (IllegalArgumentException e) {
            log.error("Can't create child on a synthetic root", e);
        } catch (NullPointerException e) {
            log.error("Current page resource is null", e);
        } finally {
            if (resourceResolver != null && resourceResolver.isLive()) {
                resourceResolver.close();
            }
        }
    }

    private static Page getCurrentPage(SlingHttpServletRequest slingRequest) {
        return Optional.ofNullable(slingRequest.getResourceResolver().adaptTo(PageManager.class))
                .map(pm -> pm.getContainingPage(slingRequest.getResource())).orElse(null);
    }

    @Override
    public void delete(SlingHttpServletRequest slingRequest) {
        ResourceResolver resourceResolver = null;
        try {
            Set<String> sourceProps = null;
            resourceResolver = getServiceResourceResolver();
            String resourceType = slingRequest.getResource().getResourceType();
            String componentName = resourceType.substring(resourceType.lastIndexOf(SLASH) + 1);
            Resource componentPath = resourceResolver.getResource(resourceType + SLASH + CLIENTLIB_FOLDER);

            if (Objects.nonNull(componentPath)) {
                ValueMap valueMap = componentPath.adaptTo(ValueMap.class);
                if (valueMap != null) {
                    sourceProps = getProps(componentPath, CLIENTLIB_CATEGORIES);
                }
            }

            Page currentPage = getCurrentPage(slingRequest);
            Resource resource = resourceResolver.getResource(CLIENTLIB_ROOT_FOLDER + currentPage.getName());

            if (Objects.nonNull(resource)) {
                ModifiableValueMap modValueMap = resource.adaptTo(ModifiableValueMap.class);
                if (modValueMap == null) {
                    log.error("No write access: Unable to store resource data to {}", resource.getPath() + ".");
                }
                if (modValueMap != null) {
                    Set<String> destProps = getProps(resource, CLIENTLIB_EMBED);
                    Long getCount = (Long) modValueMap.get(componentName);
                    if (getCount != null) {
                        modValueMap.replace(componentName, getCount - 1);
                        if (getCount <= 1) {
                            destProps.removeAll(sourceProps);
                            modValueMap.replace(CLIENTLIB_EMBED, destProps.toArray());
                        }
                    }
                }
            }
            resourceResolver.commit();
        } catch (LoginException e) {
            log.error("Unable to Login", e);
        } catch (RepositoryException e) {
            log.error("Failed to retrieve properties", e);
        } catch (PersistenceException e) {
            log.error("Failed to create counter folder {}", e.getMessage(), e);
        }
    }

    private Set<String> getProps(Resource currentResource, String propertyName) throws RepositoryException {
        return Optional.ofNullable(Arrays.stream(currentResource.adaptTo(ValueMap.class).
                get(propertyName, String[].class)).collect(Collectors.toSet())).orElse(null);
    }
}
