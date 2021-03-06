/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.php
 * See the License for the specific language governing
 * permissions and limitations under the License.
 */

/*
 * PathSegment.java
 *
 * Created on August 7, 2006, 1:24 PM
 *
 */

package org.jvnet.ws.wadl.ast;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jvnet.ws.wadl.Param;
import org.jvnet.ws.wadl.ParamStyle;
import org.jvnet.ws.wadl.Resource;
import org.jvnet.ws.wadl.ResourceType;

/**
 * Represents a segment of a URI with zero or more embedded parameters as found
 * in the path attribute of a WADL resource element. Embedded parameters are
 * represented as {name} where name is the name of the parameter. This class also
 * maintains the list of matrix parameters, query parameters and any addition
 * parameter information supplied using a child WADL param element.
 */
public class PathSegment {
    
    /**
     * A regular expression to extra out templates from a URI
     */
    public static final String PARAM_WITH_REGEX_PATTERN = "(\\{(\\w*)(\\s*:.*?)?\\})";
    /**
     * The string to use with PARAM_WITH_REGEX_PATTERN to extract the name
     */
    public static final String PARAM_WITH_REGEX_NAME = "$2";
    
    private String template;
    private List<Param> templateParameters;
    private List<Param> matrixParameters;
    private List<Param> queryParameters;
    private List<Param> headerParameters;
    
    /**
     * Construct a PathSegment instance using a string representation. All parameters
     * will be treated as string types and no matrix parameters will be specified.
     *
     * @param template the string version of the template.
     */
    public PathSegment(String template) {
        this(template, null);
    }
    
    /**
     * Construct a PathSegment instance using a string representation and a list of
     * matrix parameter names. All parameters will be treated as string types.
     *
     * @param template the string version of the template.
     * @param matrixParameters a list of matrix parameter names.
     */
    public PathSegment(String template, List<String> matrixParameters) {
        this.template = template;
        this.templateParameters = new ArrayList<Param>();
        this.matrixParameters = new ArrayList<Param>();
        this.queryParameters = new ArrayList<Param>();
        this.headerParameters = new ArrayList<Param>();

        // parse template for embedded parameters
        Pattern embeddedParamPattern = Pattern.compile("\\{.*?\\}");
        Matcher matcher = embeddedParamPattern.matcher(template);
        while (matcher.find()) {
            String paramName = matcher.group();
            paramName = paramName.substring(1,paramName.length()-1);
            Param embeddedParam = new Param();
            embeddedParam.setName(paramName);
            templateParameters.add(embeddedParam);
        }
        
        if (matrixParameters != null)  {
            for (String matrixParam: matrixParameters) {
                Param p = new Param();
                p.setName(matrixParam);
                p.setStyle(ParamStyle.MATRIX);
                this.matrixParameters.add(p);
            }
        }
    }
    
    /**
     * Creates a new instance of PathSegment from a WADL resource element.
     *
     * @param resource the WADL resource element.
     * @param file the URI of the WADL file that contains the resource element.
     * @param idMap a map of URI reference to WADL definition element.
     * @throws InvalidWADLException when WADL is invalid and cannot be processed.
     */
    public PathSegment(Resource resource, URI file, ElementResolver idMap) throws InvalidWADLException {
        template = resource.getPath() == null ? "" : resource.getPath();
        this.templateParameters = new ArrayList<Param>();
        this.matrixParameters = new ArrayList<Param>();
        this.queryParameters = new ArrayList<Param>();
        this.headerParameters = new ArrayList<Param>();
        
        // iterate through child param elements to extract params
        Map<String, Param> pathParameters = new HashMap<String, Param>();
        for (Param p: resource.getParam()) {
            p = derefIfRequired(p, file, idMap);
            if (p==null)
                continue;
            else if (p.getStyle() == null || p.getStyle() == ParamStyle.TEMPLATE)
                pathParameters.put(p.getName(), p);
            else if (p.getStyle() == ParamStyle.MATRIX)
                matrixParameters.add(p);
            else if (p.getStyle() == ParamStyle.QUERY)
                queryParameters.add(p);
            else if (p.getStyle() == ParamStyle.HEADER)
                headerParameters.add(p);
        }
        
        // parse template for embedded parameters
        Pattern embeddedParamPattern = Pattern.compile("\\{.*?\\}");
        Matcher matcher = embeddedParamPattern.matcher(template);
        while (matcher.find()) {
            String paramText = matcher.group();
            
            // It is not as simple as just removing the braces, the
            // parameter might also have a regular expression in it, we need
            // to remove of this for the purposes of code generation
            //
            //paramName = paramName.substring(1,paramName.length()-1);
            String paramName = paramText.replaceAll(
                PARAM_WITH_REGEX_PATTERN, PARAM_WITH_REGEX_NAME);
            
            // if embedded parameter is annotated by child param then use that
            // otherwise create a new empty param for it
            if (pathParameters.containsKey(paramName))
                templateParameters.add(pathParameters.get(paramName));
            else {
                Param embeddedParam = new Param();
                embeddedParam.setName(paramName);
                templateParameters.add(embeddedParam);
            }
        }
    }
    
    /**
     * Creates a new instance of PathSegment from a WADL resource type element.
     *
     * @param resource the WADL resource type element.
     * @param file the URI of the WADL file that contains the resource type element.
     * @param idMap a map of URI reference to WADL definition element.
     * @throws InvalidWADLException when WADL is invalid and cannot be processed.
     */
    public PathSegment(ResourceType resource, URI file, ElementResolver idMap) throws InvalidWADLException {
        template = null;
        templateParameters = new ArrayList<Param>();
        matrixParameters = new ArrayList<Param>();
        queryParameters = new ArrayList<Param>();
        headerParameters = new ArrayList<Param>();
        
        // iterate through child param elements to extract params
        Map<String, Param> pathParameters = new HashMap<String, Param>();
        for (Param p: resource.getParam()) {
            p = derefIfRequired(p, file, idMap);
            if (p==null)
                continue;
            else if (p.getStyle() == ParamStyle.QUERY)
                queryParameters.add(p);
            else if (p.getStyle() == ParamStyle.MATRIX)
                matrixParameters.add(p);
            else if (p.getStyle() == ParamStyle.HEADER)
                headerParameters.add(p);
        }
        
    }
    
    /**
     * Dereference a param reference element if required.
     *
     * @param p the param reference or definition.
     * @param file the URI of the WADL file containing the param reference or definition.
     * @param idMap a map of URI reference to WADL definition element.
     * @return the param definition element.
     * @throws InvalidWADLException when WADL is invalid and cannot be processed.
     */
    protected static Param derefIfRequired(Param p, URI file, ElementResolver idMap) throws InvalidWADLException {
        String href = p.getHref();
        if (href!=null && href.length()>0)
            return idMap.resolve(file, href, p);
        else
            return p;
    }
    
    /**
     * Get the underlying path segment template string.
     *
     * @return the path segment template string.
     */
    public String getTemplate() {
        return template;
    }
    
    /**
     * Get a list of parameters embedded within the underlying path segment template.
     * E.g. if the template string is {p1}/xyzzy/{p2} this will return a list of two
     * parameters: p1 and p2.
     *
     * @return the names of the parameters embedded within the template.
     */
    public List<Param> getTemplateParameters() {
        return templateParameters;
    }

    /**
     * Get list of matrix parameters attached to the path segment.
     *
     * @return a list of matrix parameter names.
     */
    public List<Param> getMatrixParameters() {
        return matrixParameters;
    }
    
    /**
     * Get list of query parameters attached to the path segment.
     *
     * @return a list of query parameter names.
     */
    public List<Param> getQueryParameters() {
        return queryParameters;
    }
    
    /**
     * Get list of header parameters attached to the path segment.
     *
     * @return a list of header parameter names.
     */
    public List<Param> getHeaderParameters() {
        return headerParameters;
    }
    
    /**
     * Merges the supplied parameter values into the path segment template and returns
     * the resulting path segment. E.g. if the template is "{p1}/{p2}" with a matrix
     * parameter p3 and the values of p1, p2 and p3 are "v1", "v2" and "v3"
     * respectively the returned path segment would be "v1/v2;p3=v3".
     * Query parameters are ignored.
     *
     * @param parameterValues a map of parameter names to values. Values can be of any class, evaluate uses
     * the object's toString method to obtain the stringified value.
     * @return the path segment resulting from inserting the parameter values into the template.
     */
    public String evaluate(Map<String, Object> parameterValues) {
        String retVal = template;
        if (parameterValues==null)
            parameterValues = new HashMap<String, Object>();
        for (Param param: templateParameters) {
            String paramName = param.getName();
            String paramValue = "";
            
            if (parameterValues.containsKey(paramName))
                paramValue = parameterValues.get(paramName).toString();
            else if (param.isRequired() == Boolean.TRUE)
                throw new IllegalArgumentException(
                    AstMessages.TEMPLATE_VALUE_MISSING(paramName));
            retVal = retVal.replaceAll("\\{"+paramName+"\\}", 
                paramValue);
        }
        StringBuilder buf = new StringBuilder(retVal);
        for (Param param: matrixParameters) {
            String paramName = param.getName();
            Object paramObject = null;
            
            if (parameterValues.containsKey(paramName)) {
                paramObject = parameterValues.get(paramName);
            } else if (param.isRequired()  == Boolean.TRUE)
                throw new IllegalArgumentException(
                    AstMessages.MATRIX_VALUE_MISSING(paramName));

            if (paramObject==null)
                continue;
            if (paramObject instanceof Boolean) {
                Boolean b = (Boolean)paramObject;
                if (b) {
                    buf.append(';');
                    buf.append(paramName);
                }
            }
            else {
                buf.append(';');
                buf.append(paramName);
                buf.append('=');
                buf.append(String.valueOf(paramObject));
            }
        }
        return buf.toString();
    }
}
