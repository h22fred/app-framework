/*! LICENSE
 *
 * Copyright (c) 2015, The Agile Factory SA and/or its affiliates. All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package models.framework_models.common;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.Version;

import models.framework_models.common.ICustomAttributeValue.AttributeType;
import models.framework_models.parent.IModel;
import models.framework_models.parent.IModelConstants;

import org.apache.commons.lang3.StringUtils;

import play.Logger;
import play.db.ebean.Model;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;

import framework.utils.DefaultSelectableValueHolder;
import framework.utils.DefaultSelectableValueHolderCollection;
import framework.utils.ISelectableValueHolderCollection;
import framework.utils.LanguageUtil;
import framework.utils.Msg;
import framework.utils.PropertiesLoader;
import framework.utils.Utilities;

/**
 * The definition for a set of {@link BooleanCustomAttributeValue}.<br/>
 * A custom attribute is an attribute which can be added to any object in the
 * system.<br/>
 * A custom attribute can be validated against some properties to be stored in
 * the database along the attribute:
 * <ul>
 * CONSTRAINT_REQUIRED_PROP : the value is required CONSTRAINT_MAX_PROP : the
 * value is lower than the specified Integer (only for number values)
 * CONSTRAINT_MIN_PROP : the value is higher than the specified Integer (only
 * for number values) CONSTRAINT_REGEXP_PROP : the value matches the specified
 * regExpr (java reg expr format, only for String values)
 * </ul>
 * Please check the properties format in the source code.
 * 
 * @author Pierre-Yves Cloux
 */
@Entity
public class CustomAttributeDefinition extends Model implements IModel {
    private static final long serialVersionUID = -7483752055757734199L;

    /*
     * A set of properties fields to be used to retrieve the configuration
     * parameters for the attribute from the "configuration" field.<br/> There
     * ones are more or less matching the Constraints annotation features of
     * play
     */
    public static final String DEFAULT_VALUE_PROP = "default.value";
    public static final String CONSTRAINT_REQUIRED_PROP = "constraint.required";
    public static final String CONSTRAINT_REQUIRED_MSG_PROP = "constraint.required.message";
    public static final String CONSTRAINT_MAX_PROP = "constraint.max";
    public static final String CONSTRAINT_MAX_MSG_PROP = "constraint.max.message";
    public static final String CONSTRAINT_MIN_PROP = "constraint.min";
    public static final String CONSTRAINT_MIN_MSG_PROP = "constraint.min.message";
    public static final String CONSTRAINT_REGEXP_PROP = "constraint.regexp";
    public static final String CONSTRAINT_REGEXP_MSG_PROP = "constraint.regexp.message";
    public static final String CONSTRAINT_AFTER_PROP = "date.after";
    public static final String CONSTRAINT_AFTER_MSG_PROP = "constraint.after.message";
    public static final String CONSTRAINT_BEFORE_PROP = "date.before";
    public static final String CONSTRAINT_BEFORE_MSG_PROP = "constraint.before.message";
    public static final String CONSTRAINT_MAX_WIDTH_PROP = "image.max.width";
    public static final String CONSTRAINT_MAX_WIDTH_MSG_PROP = "constraint.max.width.message";
    public static final String CONSTRAINT_MAX_HEIGHT_PROP = "image.max.height";
    public static final String CONSTRAINT_MAX_HEIGHT_MSG_PROP = "constraint.max.height.message";
    public static final String CONSTRAINT_NEW_WINDOW_PROP = "constraint.new_window";
    public static final String CONSTRAINT_URL_MSG_PROP = "constraint.url.message";

    public static final String DYNAMIC_SINGLE_SELECTION_QUERY_PROP = "selection.query";
    public static final String DYNAMIC_SINGLE_FILTER_WHERE_CLAUSE_PROP = "filter.where.clause";
    public static final String DYNAMIC_SINGLE_VALUE_FROM_NAME_CLAUSE_PROP = "value.from.name.where.clause";
    public static final String DYNAMIC_SINGLE_NAME_FROM_VALUE_CLAUSE_PROP = "name.from.value.where.clause";
    public static final String DYNAMIC_SINGLE_MAX_RECORDS = "max.records";
    public static final String DYNAMIC_SINGLE_INPUT_FIELD_TYPE = "input.field.type";
    public static final String DYNAMIC_SINGLE_CUSTOM_ATTRIBUTE_DEFINITION_ID_CTX_PARAMETER = "_c";

    private static Logger.ALogger log = Logger.of(CustomAttributeDefinition.class);

    public static Finder<Long, CustomAttributeDefinition> find = new Finder<Long, CustomAttributeDefinition>(Long.class, CustomAttributeDefinition.class);

    @Id
    public Long id;

    public boolean deleted = false;

    public boolean isDisplayed = true;

    @Version
    public Timestamp lastUpdate;

    @Column(length = IModelConstants.LARGE_STRING)
    public String uuid;

    @Column(length = IModelConstants.MEDIUM_STRING)
    public String name;

    @Column(length = IModelConstants.VLARGE_STRING)
    public String description;

    @Column(length = IModelConstants.LARGE_STRING)
    public String objectType;

    @Lob
    public byte[] configuration;

    @Column(length = IModelConstants.SMALL_STRING)
    public String attributeType;

    @Column(name = "`order`", scale = 5)
    public int order;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<BooleanCustomAttributeValue> booleanCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<IntegerCustomAttributeValue> integerCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<DecimalCustomAttributeValue> decimalCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<DateCustomAttributeValue> dateCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<StringCustomAttributeValue> stringCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<TextCustomAttributeValue> textCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<UrlCustomAttributeValue> urlCustomAttributeValues;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<SingleItemCustomAttributeValue> singleItemCustomAttributeValue;

    @OneToMany(mappedBy = "customAttributeDefinition", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<CustomAttributeItemOption> customAttributeItemOption;

    /**
     * Return the properties associated with this custom attribute definition.<br/>
     * The properties might define some specific configuration attributes
     * 
     * @return a properties object
     */
    public Properties getProperties() {
        if (this.configuration != null) {
            try {
                return PropertiesLoader.loadProperties(new ByteArrayInputStream(this.configuration), "UTF-8");
            } catch (Exception e) {
                log.error("Unable to parse the properties", e);
            }
        }
        return new Properties();
    }

    @Override
    public String audit() {
        return "CustomAttributeDefinition [id=" + id + ", deleted=" + deleted + ", lastUpdate=" + lastUpdate + ", name=" + name + ", attributeType="
                + attributeType + ", description=" + description + ", objectType=" + objectType + ", order=" + order + "]";
    }

    @Override
    public void defaults() {
    }

    @Override
    public void doDelete() {
        deleted = true;
        save();
    }

    @Override
    public String toString() {
        return uuid;
    }

    public String getNameLabel() {
        return Msg.get(name);
    }

    /*
     * --------------------------------------------------------------------------
     * ----- --------------- Constraints & properties management
     * ---------------------------
     * ----------------------------------------------
     * ---------------------------------
     */
    public String getDefaultValueAsString() {
        return getProperties().getProperty(DEFAULT_VALUE_PROP);
    }

    public String[] getDefaultValueAsArrayOfString() {
        String commaSeparatedDefaultValues = getProperties().getProperty(DEFAULT_VALUE_PROP);
        if (commaSeparatedDefaultValues == null)
            return new String[] {};
        return StringUtils.split(commaSeparatedDefaultValues, ",");
    }

    public boolean isRequired() {
        return !StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_REQUIRED_PROP));
    }

    public String getRequiredMessage() {
        return getProperties().getProperty(CONSTRAINT_REQUIRED_MSG_PROP, "error.required");
    }

    /*
     * --------------- Integer and Decimal properties
     * ---------------------------
     */
    public int maxBoundary() {
        if (!StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_MAX_PROP))) {
            try {
                return Integer.parseInt(getProperties().getProperty(CONSTRAINT_MAX_PROP));
            } catch (Exception e) {
            }
        }
        return Integer.MAX_VALUE;
    }

    public String getMaxBoundaryMessage() {
        return getProperties().getProperty(CONSTRAINT_MAX_MSG_PROP, "error.max");
    }

    public int minBoundary() {
        if (!StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_MIN_PROP))) {
            try {
                return Integer.parseInt(getProperties().getProperty(CONSTRAINT_MIN_PROP));
            } catch (Exception e) {
            }
        }
        return Integer.MIN_VALUE;
    }

    public String getMinBoundaryMessage() {
        return getProperties().getProperty(CONSTRAINT_MIN_MSG_PROP, "error.min");
    }

    /*
     * --------------- String properties ---------------------------
     */
    public String validationRegExpr() {
        return getProperties().getProperty(CONSTRAINT_REGEXP_PROP);
    }

    public String getValidationRegExprMessage() {
        return getProperties().getProperty(CONSTRAINT_REGEXP_MSG_PROP, "error.invalid");
    }

    /*
     * --------------- Date properties ---------------------------
     */
    public Date getDateBeforeBoundary() {
        String referenceDateAsString = getProperties().getProperty(CONSTRAINT_BEFORE_PROP);
        if (referenceDateAsString == null)
            return null;
        Date referenceDate = null;
        try {
            Integer offSet = Integer.parseInt(referenceDateAsString);
            referenceDate = Utilities.getOffSetDate(offSet);
        } catch (NumberFormatException e) {
            log.warn("The property CONSTRAINT_BEFORE_PROP for the custom attribute " + uuid + "is invalid");
            return null;
        }
        return referenceDate;
    }

    public Date getDateAfterBoundary() {
        String referenceDateAsString = getProperties().getProperty(CONSTRAINT_AFTER_PROP);
        if (referenceDateAsString == null)
            return null;
        Date referenceDate = null;
        try {
            Integer offSet = Integer.parseInt(referenceDateAsString);
            referenceDate = Utilities.getOffSetDate(offSet);
        } catch (NumberFormatException e) {
            log.warn("The property CONSTRAINT_AFTER_PROP for the custom attribute " + uuid + "is invalid");
            return null;
        }
        return referenceDate;
    }

    public boolean isDateBefore(Date dateToCheck) {
        Date referenceDate = getDateBeforeBoundary();
        if (referenceDate == null)
            return true;
        return dateToCheck.before(referenceDate);
    }

    public boolean isDateAfter(Date dateToCheck) {
        Date referenceDate = getDateAfterBoundary();
        if (referenceDate == null)
            return true;
        return dateToCheck.after(referenceDate);
    }

    public String getDateAfterMessage() {
        return getProperties().getProperty(CONSTRAINT_AFTER_MSG_PROP, "form.input.date.error.after");
    }

    public String getDateBeforeMessage() {
        return getProperties().getProperty(CONSTRAINT_BEFORE_MSG_PROP, "form.input.date.error.before");
    }

    /*
     * --------------- Dynamic single properties ------------------
     */
    public String getSelectionQuery() {
        return getProperties().getProperty(DYNAMIC_SINGLE_SELECTION_QUERY_PROP);
    }

    public String getFilterWhereClause() {
        return getProperties().getProperty(DYNAMIC_SINGLE_FILTER_WHERE_CLAUSE_PROP);
    }

    public String getValueFromNameWhereClause() {
        return getProperties().getProperty(DYNAMIC_SINGLE_VALUE_FROM_NAME_CLAUSE_PROP);
    }

    public String getNameFromValueWhereClause() {
        return getProperties().getProperty(DYNAMIC_SINGLE_NAME_FROM_VALUE_CLAUSE_PROP);
    }

    public Integer getDynamicSingleMaxRecords() {
        String maxRecordsAsString = getProperties().getProperty(DYNAMIC_SINGLE_MAX_RECORDS);
        if (!StringUtils.isBlank(maxRecordsAsString)) {
            try {
                return Integer.parseInt(maxRecordsAsString);
            } catch (NumberFormatException e) {
            }
        }
        return 10;
    }

    /*
     * --------------- File properties ------------------
     */

    public Integer maxWidth() {
        if (!StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_MAX_WIDTH_PROP))) {
            try {
                return Integer.parseInt(getProperties().getProperty(CONSTRAINT_MAX_WIDTH_PROP));
            } catch (Exception e) {
            }
        }
        return null;
    }

    public String getMaxWidthMessage() {
        return getProperties().getProperty(CONSTRAINT_MAX_WIDTH_MSG_PROP, "form.input.image.error.max_width");
    }

    public Integer maxHeight() {
        if (!StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_MAX_HEIGHT_PROP))) {
            try {
                return Integer.parseInt(getProperties().getProperty(CONSTRAINT_MAX_HEIGHT_PROP));
            } catch (Exception e) {
            }
        }
        return null;
    }

    public String getMaxHeightMessage() {
        return getProperties().getProperty(CONSTRAINT_MAX_HEIGHT_MSG_PROP, "form.input.image.error.max_height");
    }

    /*
     * --------------- url properties ------------------
     */

    public boolean isNewWindow() {
        if (!StringUtils.isBlank(getProperties().getProperty(CONSTRAINT_NEW_WINDOW_PROP))) {
            try {
                return Boolean.parseBoolean(getProperties().getProperty(CONSTRAINT_NEW_WINDOW_PROP));
            } catch (Exception e) {
            }
        }
        return false;
    }

    public String getUrlMessage() {
        return getProperties().getProperty(CONSTRAINT_URL_MSG_PROP, "form.input.url.invalid");
    }

    /**
     * Is an auto complete attribute.
     */
    public boolean isAutoComplete() {
        if (getProperties().getProperty(DYNAMIC_SINGLE_INPUT_FIELD_TYPE).equals("DROPDOWN")) {
            return false;
        }
        return true;
    }

    /**
     * The context parameters passed in the context of the dynamic attribute
     * when autocomplete is selected.<br/>
     * This is required so that the generic JSON API can find the right custom
     * attribute definition
     * 
     * @return a map of attributes
     */
    public Map<String, String> getContextParametersForDynamicApi() {
        HashMap<String, String> contextParameters = new HashMap<String, String>();
        contextParameters.put(DYNAMIC_SINGLE_CUSTOM_ATTRIBUTE_DEFINITION_ID_CTX_PARAMETER, String.valueOf(id));
        return contextParameters;
    }

    /**
     * Return the default value as a "value" (instead of name)
     * 
     * @return if not found 0 is returned
     */
    public Long getDynamicDefaultValueAsLong() {
        Long value = getValueFromName(getDefaultValueAsString());
        if (value == null) {
            if (log.isDebugEnabled()) {
                log.debug("Default value for custom attribute " + uuid + " is null or not found !");
            }
        }
        return value;
    }

    /**
     * Return the list of values for a
     * {@link DynamicSingleItemCustomAttributeValue}
     * 
     * @param searchstring
     *            the string entered by the user in the autocomplete field
     * @param uid
     *            the uid of the current user
     */
    public ISelectableValueHolderCollection<Long> getValueHoldersCollectionFromNameForDynamicSingleItemCustomAttribute(String searchstring, String uid) {
        String sql = getSelectionQuery() + " " + getFilterWhereClause();
        if (getDynamicSingleMaxRecords() != 0 && isAutoComplete()) {
            sql = sql + " LIMIT " + getDynamicSingleMaxRecords();
        }
        if (log.isDebugEnabled()) {
            log.debug("SQL Query : " + sql);
        }
        SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
        sqlQuery.setParameter("searchstring", searchstring + "%");
        sqlQuery.setParameter("uid", uid);
        sqlQuery.setParameter("lang", LanguageUtil.getCurrent().getCode());
        if (log.isDebugEnabled()) {
            log.debug("uid passed as a parameter : " + uid);
        }
        List<SqlRow> rows = sqlQuery.findList();
        DefaultSelectableValueHolderCollection<Long> valueHolders = new DefaultSelectableValueHolderCollection<Long>();
        if (rows != null) {
            for (SqlRow row : rows) {
                valueHolders.add(new DefaultSelectableValueHolder<Long>(row.getLong("value"), row.getString("name")));
            }
        }
        return valueHolders;
    }

    /**
     * Returns the list of valid values for a
     * {@link SingleItemCustomAttributeValue}
     * 
     * @return
     */
    public ISelectableValueHolderCollection<Long> getValueHoldersCollectionForSingleItemCustomAttribute() {
        return CustomAttributeItemOption.getSelectableValuesForDefinitionId(id);
    }

    /**
     * Returns the list of valid values for a
     * {@link MultiItemCustomAttributeValue}
     */
    public ISelectableValueHolderCollection<Long> getValueHoldersCollectionForMultiItemCustomAttribute() {
        return CustomAttributeMultiItemOption.getSelectableValuesForDefinitionId(id);
    }

    /**
     * Return a value from the specified name using the configured SQL queries
     * 
     * @return a long value
     */
    public Long getValueFromName(String nameOfValueHolder) {
        try {
            String sql = getSelectionQuery() + " " + getValueFromNameWhereClause();
            if (log.isDebugEnabled()) {
                log.debug("SQL Query : " + sql);
            }
            SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
            sqlQuery.setParameter("nametofind", nameOfValueHolder);
            SqlRow row = sqlQuery.findUnique();
            return row != null ? row.getLong("value") : null;
        } catch (RuntimeException e) {
            log.error(String.format("Error with custom attribute %s with name=%s", uuid, nameOfValueHolder));
            throw e;
        }
    }

    /**
     * Return a value from the specified name using the configured SQL queries
     * 
     * @return a long value
     */
    public String getNameFromValue(Long valueOfValueHolder) {
        try {
            String sql = getSelectionQuery() + " " + getNameFromValueWhereClause();
            if (log.isDebugEnabled()) {
                log.debug("SQL Query : " + sql);
            }
            SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
            sqlQuery.setParameter("lang", LanguageUtil.getCurrent().getCode());
            sqlQuery.setParameter("valuetofind", valueOfValueHolder);
            SqlRow row = sqlQuery.findUnique();
            return row != null ? row.getString("name") : null;
        } catch (RuntimeException e) {
            log.error(String.format("Error with custom attribute %s with value=%s", uuid, String.valueOf(valueOfValueHolder)));
            throw e;
        }
    }

    /*
     * --------------------------------------------------------------------------
     * ----- --------------- Constraints & properties management (end)
     * ---------------------
     * ----------------------------------------------------
     * ---------------------------
     */

    /**
     * Return the ordered (against the order attribute) list of custom attribute
     * definitions for the specified object type
     * 
     * @param objectType
     *            an object type
     * @return a list of custom attribute definitions
     */
    public static List<CustomAttributeDefinition> getOrderedCustomAttributeDefinitions(Class<?> objectType) {
        return find.where().eq("deleted", false).eq("objectType", objectType.getName()).orderBy("order").findList();
    }

    /**
     * Return the ordered (against the order attribute) list of custom attribute
     * definitions for the specified object type
     * 
     * @param objectType
     *            an object type
     * @param filter
     *            a filter which extends the name of the object type in order to
     *            add an additional level. For instance if we'd like to have
     *            custom attributes for PorfolioEntry but that would be only for
     *            certain types of display mode (= only for reports)
     * @return a list of custom attribute definitions
     */
    public static List<CustomAttributeDefinition> getOrderedCustomAttributeDefinitions(Class<?> objectType, String filter) {
        return find.where().eq("deleted", false).eq("objectType", objectType.getName() + ":" + filter).orderBy("order").findList();
    }

    /*
     * 
     * 
     * 
     * 
     */

    /**
     * Return the definition associated with the specified uuid and objectType
     * 
     * @param id
     *            the id of a definition
     * 
     * @return a definition object or null
     */
    public static CustomAttributeDefinition getCustomAttributeDefinitionFromId(Long id) {
        return find.where().eq("deleted", false).eq("id", id).findUnique();
    }

    /**
     * Get the custom attribute of an object type with the previous order.
     * 
     * @param objecType
     *            the object type
     * @param order
     *            the current order
     */
    public static CustomAttributeDefinition getPrevious(Class<?> objectType, int order) {
        return find.orderBy("order DESC").where().eq("deleted", false).eq("objectType", objectType.getName()).lt("order", order).setMaxRows(1).findUnique();
    }

    /**
     * Get the custom attribute of an object type with the next order.
     * 
     * @param objecType
     *            the object type
     * @param order
     *            the current order
     */
    public static CustomAttributeDefinition getNext(Class<?> objectType, int order) {
        return find.orderBy("order ASC").where().eq("deleted", false).eq("objectType", objectType.getName()).gt("order", order).setMaxRows(1).findUnique();
    }

    /**
     * Get the last order for an object type.
     * 
     * @param objecType
     *            the object type
     */
    public static Integer getLastOrder(Class<?> objectType) {
        CustomAttributeDefinition lastCustomAttributeDefinition =
                find.orderBy("order DESC").where().eq("deleted", false).eq("objectType", objectType.getName()).setMaxRows(1).findUnique();
        if (lastCustomAttributeDefinition == null) {
            return -1;
        } else {
            return lastCustomAttributeDefinition.order;
        }
    }

    /**
     * Return the ordered (against the order attribute) list of custom
     * attributes for the specified object type
     * 
     * @param objectType
     *            an object type
     * @param objectId
     *            the id of an object
     * @return a list of values
     */
    public static List<ICustomAttributeValue> getOrderedCustomAttributeValues(Class<?> objectType, Long objectId) {
        List<CustomAttributeDefinition> customAttributeDefinitions = getOrderedCustomAttributeDefinitions(objectType);
        if (customAttributeDefinitions != null) {
            List<ICustomAttributeValue> customAttributeValues = new ArrayList<ICustomAttributeValue>(customAttributeDefinitions.size());
            for (CustomAttributeDefinition customAttributeDefinition : customAttributeDefinitions) {
                customAttributeValues.add(getOrCreateCustomAttributeValue(objectType, objectId, customAttributeDefinition));
            }
            return customAttributeValues;
        }
        return null;
    }

    /**
     * Return the ordered (against the order attribute) list of custom
     * attributes for the specified object type and a filter.
     * 
     * A filter is useful to reduce the existence of a custom field to a subset
     * of the instances of an object type (for example all instances for which a
     * field is equal to a specific value).
     * 
     * In the DB, the object type column should be constructed as:<br/>
     * {objectType}:{filter}
     * 
     * @param objectType
     *            an object type
     * @param filter
     *            a filter which extends the name of the object type in order to
     *            add an additional level. For instance if we'd like to have
     *            custom attributes for PorfolioEntry but that would be only for
     *            certain types of display mode (= only for reports)
     * @param objectId
     *            the id of an object
     * @return a list of values
     */
    public static List<ICustomAttributeValue> getOrderedCustomAttributeValues(Class<?> objectType, String filter, Long objectId) {
        List<CustomAttributeDefinition> customAttributeDefinitions = getOrderedCustomAttributeDefinitions(objectType, filter);
        if (customAttributeDefinitions != null) {
            List<ICustomAttributeValue> customAttributeValues = new ArrayList<ICustomAttributeValue>(customAttributeDefinitions.size());
            for (CustomAttributeDefinition customAttributeDefinition : customAttributeDefinitions) {
                customAttributeValues.add(getOrCreateCustomAttributeValue(objectType, filter, objectId, customAttributeDefinition));
            }
            return customAttributeValues;
        }
        return null;
    }

    /**
     * Define if an object type has at least one defined custom attribute. This
     * method returns true even if the custom attribute doesn't have a value for
     * a specific instance
     * 
     * @param objectType
     *            the object type
     * 
     * @return return true if the object type has at lest one custom attribute
     */
    public static Boolean hasCustomAttributes(Class<?> objectType) {
        List<CustomAttributeDefinition> customAttributeDefinitions = getOrderedCustomAttributeDefinitions(objectType);
        if (customAttributeDefinitions != null && customAttributeDefinitions.size() > 0) {
            return true;
        }
        return false;
    }

    public static Boolean hasCustomAttributes(Class<?> objectType, String filter) {
        List<CustomAttributeDefinition> customAttributeDefinitions = getOrderedCustomAttributeDefinitions(objectType, filter);
        if (customAttributeDefinitions != null && customAttributeDefinitions.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Return an attribute value associated with the specified definition
     * 
     * @param attributeDefinitionId
     *            the id of the {@link CustomAttributeDefinition}
     * @param objectType
     *            an object type
     * @param objectId
     *            the id of an object
     * @return a value
     */
    public static ICustomAttributeValue getCustomAttributeValue(Long attributeDefinitionId, Class<?> objectType, Long objectId) {
        CustomAttributeDefinition customAttributeDefinition =
                find.where().eq("deleted", false).eq("objectType", objectType.getName()).eq("id", attributeDefinitionId).findUnique();
        return getOrCreateCustomAttributeValue(objectType, objectId, customAttributeDefinition);
    }

    /**
     * Return an attribute value associated with the specified definition
     * 
     * @param attributeDefinitionId
     *            the id of the {@link CustomAttributeDefinition}
     * @param objectType
     *            an object type
     * @param filter
     *            a filter which extends the name of the object type in order to
     *            add an additional level. For instance if we'd like to have
     *            custom attributes for PorfolioEntry but that would be only for
     *            certain types of display mode (= only for reports)
     * @param objectId
     *            the id of an object
     * @return a value
     */
    public static ICustomAttributeValue getCustomAttributeValue(Long attributeDefinitionId, Class<?> objectType, String filter, Long objectId) {
        CustomAttributeDefinition customAttributeDefinition =
                find.where().eq("deleted", false).eq("objectType", objectType.getName() + ":" + filter).eq("id", attributeDefinitionId).findUnique();
        return getOrCreateCustomAttributeValue(objectType, filter, objectId, customAttributeDefinition);
    }

    /**
     * Return an attribute value associated with the specified definition
     * 
     * @param attributeDefinitionUuid
     *            the uuid of the {@link CustomAttributeDefinition}
     * @param objectType
     *            an object type
     * @param objectId
     *            the id of an object
     * @return a value
     */
    public static ICustomAttributeValue getCustomAttributeValue(String attributeDefinitionUuid, Class<?> objectType, Long objectId) {
        CustomAttributeDefinition customAttributeDefinition =
                find.where().eq("deleted", false).eq("objectType", objectType.getName()).eq("uuid", attributeDefinitionUuid).findUnique();
        return getOrCreateCustomAttributeValue(objectType, objectId, customAttributeDefinition);
    }

    public static ICustomAttributeValue getCustomAttributeValue(String attributeDefinitionUuid, Class<?> objectType, String filter, Long objectId) {
        CustomAttributeDefinition customAttributeDefinition =
                find.where().eq("deleted", false).eq("objectType", objectType.getName() + ":" + filter).eq("uuid", attributeDefinitionUuid).findUnique();
        return getOrCreateCustomAttributeValue(objectType, filter, objectId, customAttributeDefinition);
    }

    /**
     * Returns the {@link ICustomAttributeValue} associated with the specified
     * objectType and objectId couple
     * 
     * @param objectType
     *            an object type (java object name)
     * @param objectId
     *            the id of an object
     * @param customAttributeDefinition
     *            the definition of a custom attribute
     * @return an custom attribute instance
     */
    private static ICustomAttributeValue getOrCreateCustomAttributeValue(Class<?> objectType, Long objectId,
            CustomAttributeDefinition customAttributeDefinition) {
        return getOrCreateCustomAttributeValue(objectType, null, objectId, customAttributeDefinition);
    }

    private static ICustomAttributeValue getOrCreateCustomAttributeValue(Class<?> objectType, String filter, Long objectId,
            CustomAttributeDefinition customAttributeDefinition) {
        if (log.isDebugEnabled()) {
            log.debug("Object type [" + objectType + "] objectId [" + objectId + "] Definition [" + customAttributeDefinition + "]");
        }
        switch (AttributeType.valueOf(customAttributeDefinition.attributeType)) {
        case INTEGER:
            return IntegerCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case DECIMAL:
            return DecimalCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case BOOLEAN:
            return BooleanCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case DATE:
            return DateCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case STRING:
            return StringCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case TEXT:
            return TextCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case URL:
            return UrlCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case SINGLE_ITEM:
            return SingleItemCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case DYNAMIC_SINGLE_ITEM:
            return DynamicSingleItemCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId,
                    customAttributeDefinition);
        case MULTI_ITEM:
            return MultiItemCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        case IMAGE:
            return ImageCustomAttributeValue.getOrCreateCustomAttributeValueFromObjectReference(objectType, filter, objectId, customAttributeDefinition);
        default:
            throw new IllegalArgumentException("Invalid attribute type : " + customAttributeDefinition.attributeType);
        }
    }

}
