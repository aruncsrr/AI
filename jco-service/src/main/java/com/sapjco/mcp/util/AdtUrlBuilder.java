package com.sapjco.mcp.util;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for building ADT (ABAP Development Tools) REST API URLs.
 * Provides consistent URL construction for different object types and operations.
 */
public final class AdtUrlBuilder {

    private AdtUrlBuilder() {
        // Utility class - prevent instantiation
    }

    // ADT base path
    public static final String ADT_BASE = "/sap/bc/adt";

    // Object type paths
    public static final String PATH_CLASSES = ADT_BASE + "/oo/classes/";
    public static final String PATH_INTERFACES = ADT_BASE + "/oo/interfaces/";
    public static final String PATH_PROGRAMS = ADT_BASE + "/programs/programs/";
    public static final String PATH_INCLUDES = ADT_BASE + "/programs/includes/";
    public static final String PATH_FUNCTION_GROUPS = ADT_BASE + "/functions/groups/";
    public static final String PATH_PACKAGES = ADT_BASE + "/packages/";
    public static final String PATH_TABLES = ADT_BASE + "/ddic/tables/";
    public static final String PATH_STRUCTURES = ADT_BASE + "/ddic/tables/";  // Same as tables
    public static final String PATH_DATA_ELEMENTS = ADT_BASE + "/ddic/dataelements/";
    public static final String PATH_DOMAINS = ADT_BASE + "/ddic/domains/";
    public static final String PATH_CDS_VIEWS = ADT_BASE + "/ddic/ddl/sources/";
    public static final String PATH_BOPF = ADT_BASE + "/bopf/businessobjects/";
    public static final String PATH_BEHAVIOR_DEFINITIONS = ADT_BASE + "/bo/behaviordefinitions/";
    public static final String PATH_SERVICE_DEFINITIONS = ADT_BASE + "/ddic/srvd/sources/";
    public static final String PATH_SERVICE_BINDINGS = ADT_BASE + "/businessservices/bindings/";

    /**
     * Build ADT URI path for source code access (includes /source/main suffix).
     * Used for reading and saving source code.
     *
     * @param objectType Object type (class, interface, program, function_group, include)
     * @param objectName Object name (may be namespaced like /SCMTMS/CL_TOR)
     * @return Full ADT URI path for source access
     * @throws IllegalArgumentException if the object type is not supported
     */
    public static String buildSourceUrl(String objectType, String objectName) {
        String baseUrl = buildBaseUrl(objectType, objectName);
        return baseUrl + "/source/main";
    }

    /**
     * Build ADT URI path for object root (without /source/main suffix).
     * Used for locking, activation, metadata access, and where-used queries.
     *
     * @param objectType Object type (class, interface, program, function_group, include, etc.)
     * @param objectName Object name (may be namespaced like /SCMTMS/CL_TOR)
     * @return Full ADT URI path for object root
     * @throws IllegalArgumentException if the object type is not supported
     */
    public static String buildBaseUrl(String objectType, String objectName) {
        String encodedName = ObjectNameEncoder.encode(objectName);
        return switch (objectType.toLowerCase()) {
            case "class" -> PATH_CLASSES + encodedName;
            case "interface" -> PATH_INTERFACES + encodedName;
            case "program" -> PATH_PROGRAMS + encodedName;
            case "function_group" -> PATH_FUNCTION_GROUPS + encodedName;
            case "include" -> PATH_INCLUDES + encodedName;
            case "package" -> PATH_PACKAGES + encodedName;
            case "table" -> PATH_TABLES + encodedName;
            case "structure" -> PATH_STRUCTURES + encodedName;
            case "data_element" -> PATH_DATA_ELEMENTS + encodedName;
            case "domain" -> PATH_DOMAINS + encodedName;
            case "cds_view" -> PATH_CDS_VIEWS + encodedName;
            case "bopf", "business_object" -> PATH_BOPF + encodedName;
            case "behavior_definition" -> PATH_BEHAVIOR_DEFINITIONS + encodedName;
            case "service_definition" -> PATH_SERVICE_DEFINITIONS + encodedName;
            case "service_binding" -> PATH_SERVICE_BINDINGS + encodedName;
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
    }

    /**
     * Build ADT URI path for lock/unlock operations.
     * This is the same as buildBaseUrl - lock operations use the object root URI.
     *
     * @param objectType Object type (class, interface, program, function_group, include)
     * @param objectName Object name (may be namespaced)
     * @return Full ADT URI path for lock operations
     */
    public static String buildLockUrl(String objectType, String objectName) {
        return buildBaseUrl(objectType, objectName);
    }

    /**
     * Build a full URL with query parameters.
     *
     * @param baseUri The base URI path
     * @param params Map of query parameter names to values (null values are ignored)
     * @return URI with query string appended
     */
    public static String buildQueryString(String baseUri, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUri;
        }

        String queryString = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + ObjectNameEncoder.urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        if (queryString.isEmpty()) {
            return baseUri;
        }

        return baseUri + (baseUri.contains("?") ? "&" : "?") + queryString;
    }

    /**
     * Build ADT URI for class includes (definitions, implementations, macros, testClasses, localTypes).
     *
     * @param className Class name
     * @param includeType Include type (definitions, implementations, macros, testClasses, localTypes)
     * @return Full ADT URI path for the class include
     */
    public static String buildClassIncludeUrl(String className, String includeType) {
        String encodedName = ObjectNameEncoder.encode(className);
        return PATH_CLASSES + encodedName + "/includes/" + includeType;
    }

    /**
     * Build ADT URI for class include source.
     *
     * @param className Class name
     * @param includeType Include type (definitions, implementations, macros, testClasses, localTypes)
     * @return Full ADT URI path for the class include source
     */
    public static String buildClassIncludeSourceUrl(String className, String includeType) {
        return buildClassIncludeUrl(className, includeType) + "/source/main";
    }

    /**
     * Build ADT URI for function module within a function group.
     *
     * @param groupName Function group name
     * @param moduleName Function module name
     * @return Full ADT URI path for the function module
     */
    public static String buildFunctionModuleUrl(String groupName, String moduleName) {
        String encodedGroup = ObjectNameEncoder.encode(groupName);
        String encodedModule = ObjectNameEncoder.encode(moduleName);
        return PATH_FUNCTION_GROUPS + encodedGroup + "/fmodules/" + encodedModule;
    }

    /**
     * Build ADT URI for function module source code.
     *
     * @param groupName Function group name
     * @param moduleName Function module name
     * @return Full ADT URI path for the function module source
     */
    public static String buildFunctionModuleSourceUrl(String groupName, String moduleName) {
        return buildFunctionModuleUrl(groupName, moduleName) + "/source/main";
    }

    /**
     * Build ADT URI for a function group include.
     *
     * @param groupName Function group name
     * @param includeName Include name (e.g., "LZFG_TESTF01")
     * @return Full ADT URI path for the function group include
     */
    public static String buildFunctionGroupIncludeUrl(String groupName, String includeName) {
        String encodedGroup = ObjectNameEncoder.encode(groupName);
        String encodedInclude = ObjectNameEncoder.encode(includeName);
        return PATH_FUNCTION_GROUPS + encodedGroup + "/includes/" + encodedInclude;
    }

    /**
     * Build ADT URI for function group include source code.
     *
     * @param groupName Function group name
     * @param includeName Include name (e.g., "LZFG_TESTF01")
     * @return Full ADT URI path for the function group include source
     */
    public static String buildFunctionGroupIncludeSourceUrl(String groupName, String includeName) {
        return buildFunctionGroupIncludeUrl(groupName, includeName) + "/source/main";
    }

    /**
     * Get the ADT type code for a given object type.
     * Used in XML requests (e.g., where-used queries).
     *
     * @param objectType Object type
     * @return ADT type code
     */
    public static String getAdtTypeCode(String objectType) {
        return switch (objectType.toLowerCase()) {
            case "class" -> "CLAS/OC";
            case "interface" -> "INTF/OI";
            case "program" -> "PROG/P";
            case "function_group" -> "FUGR/F";
            case "function_module" -> "FUGR/FF";
            case "data_element" -> "DTEL/DE";
            case "table" -> "TABL/DT";
            case "structure" -> "TABL/DS";
            case "domain" -> "DOMA/DO";
            case "cds_view" -> "DDLS/DF";
            case "package" -> "DEVC/K";
            case "search_help" -> "SHLP/SH";
            case "message_class" -> "MSAG/N";
            case "include" -> "PROG/I";
            case "badi_definition" -> "ENHS/BDO";
            case "badi_implementation" -> "ENHO/BIK";
            case "behavior_definition" -> "BDEF/BDO";
            case "service_definition" -> "SRVD/SRV";
            case "service_binding" -> "SRVB/SVB";
            default -> objectType.toUpperCase();
        };
    }
}
