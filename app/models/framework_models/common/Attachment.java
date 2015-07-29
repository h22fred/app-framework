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

import java.sql.Timestamp;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Version;

import models.framework_models.parent.IModel;
import models.framework_models.parent.IModelConstants;
import play.db.ebean.Model;

import com.avaje.ebean.ExpressionList;

/**
 * An attachment is a file or a {@link StructuredDocument} attached to another
 * object.<br/>
 * There is no relationship between the attachment object and the other model
 * objects.<br/>
 * Indeed, potentially, any object can be associated with an attachment. There
 * are three possible types of attachments:
 * <ul>
 * <li>A link to a file on the file system</li>
 * <li>A structured document (that is to say a XML or other data stored into the
 * database)</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 */
@Entity
public class Attachment extends Model implements IModel {
    private static final long serialVersionUID = -7414585552634927718L;

    /**
     * Default finder for the entity class
     */
    public static Finder<Long, Attachment> find = new Finder<Long, Attachment>(Long.class, Attachment.class);

    public boolean deleted = false;
    @Version
    public Timestamp lastUpdate;

    @Id
    public Long id;

    @Column(length = IModelConstants.LARGE_STRING)
    public String name;

    @Column(length = IModelConstants.LARGE_STRING)
    public String path;

    @Column(length = IModelConstants.LARGE_STRING)
    public String mimeType;

    @Column(length = IModelConstants.LARGE_STRING)
    public String objectType;

    public Long objectId;

    @OneToOne(optional = true)
    public StructuredDocument structuredDocument;

    public Attachment() {
    }

    /**
     * Return true if the attachment is a file attachment
     * 
     * @return a boolean
     */
    public boolean isFile() {
        return this.path != null;
    }

    @Override
    public String audit() {
        return "Attachment [id=" + id + ", name=" + name + ", path=" + path + ", mimeType=" + mimeType + ", objectType=" + objectType + ", objectId="
                + objectId + ", structuredDocument=" + structuredDocument + "]";
    }

    @Override
    public void defaults() {

    }

    @Override
    public void doDelete() {
        this.deleted = true;
        save();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Return the attachment associated with the specified unique id
     * 
     * @param id
     *            an Id
     * @return an attachment or null if not found
     */
    public static Attachment getAttachmentFromId(Long id) {
        return Attachment.find.where().eq("deleted", false).eq("id", id).findUnique();
    }

    public static ExpressionList<Attachment> getAttachmentsFromObjectTypeAndObjectIdAsExpressionList(Class<?> objectType, Long objectId) {
        return find.where().eq("deleted", false).eq("objectType", objectType.getName()).eq("objectId", objectId);
    }

    /**
     * Return a list of attachments associated with the specified objectType and
     * objectId
     * 
     * @param objectType
     *            an objectType
     * @param objectId
     *            an objectId
     * @return a list of attachments
     */
    public static List<Attachment> getAttachmentsFromObjectTypeAndObjectId(Class<?> objectType, Long objectId) {
        return getAttachmentsFromObjectTypeAndObjectIdAsExpressionList(objectType, objectId).findList();
    }

    /**
     * Return a list of attachments associated with the specified objectType and
     * objectId.<br/>
     * Filter either file files or the structured documents
     * 
     * @param objectType
     *            an objectType
     * @param objectId
     *            an objectId
     * @param structuredDocument
     *            if true returns only the structured documents, if false
     *            returns only the files
     * @return a list of attachments
     */
    public static List<Attachment> getAttachmentsFromObjectTypeAndObjectId(Class<?> objectType, Long objectId, boolean structuredDocument) {
        if (structuredDocument) {
            return getAttachmentsFromObjectTypeAndObjectIdAsExpressionList(objectType, objectId).isNotNull("structuredDocument").findList();
        }
        return getAttachmentsFromObjectTypeAndObjectIdAsExpressionList(objectType, objectId).isNull("structuredDocument").findList();
    }

    /**
     * Count the number of attachments associated with the specified objectType
     * and objectId
     * 
     * @param objectType
     *            an objectType
     * @param objectId
     *            an objectId
     * @return a number of instances
     */
    public static int getNumberOfAttachments(Class<?> objectType, Long objectId) {
        return getAttachmentsFromObjectTypeAndObjectIdAsExpressionList(objectType, objectId).findRowCount();
    }

    /**
     * Count the number of attachments associated with the specified path
     * 
     * @param path
     *            a file path
     * @return a number of instances
     */
    public static int getNumberOfAttachments(String path) {
        return Attachment.find.where().eq("deleted", false).eq("path", path).findRowCount();
    }

    /**
     * Count the number of attachments associated with the specified structured
     * document id
     * 
     * @param path
     *            a file path
     * @return a number of instances
     */
    public static int getNumberOfAttachments(Long structuredDocumentId) {
        return Attachment.find.where().eq("deleted", false).eq("structuredDocument.id", structuredDocumentId).findRowCount();
    }

    /**
     * Find all the attachments associated with the same "path"
     * 
     * @param path
     *            a file path
     * @return a list of attachments
     */
    public static List<Attachment> getAttachmentsFromPath(String path) {
        return Attachment.find.where().eq("deleted", false).eq("path", path).findList();
    }

    /**
     * Find all the attachments associated with the same structured document id
     * 
     * @param path
     *            a file path
     * @return a list of attachments
     */
    public static List<Attachment> getAttachmentsFromStructuredDocumentId(Long structuredDocumentId) {
        return Attachment.find.where().eq("deleted", false).eq("structuredDocument.id", structuredDocumentId).findList();
    }
}
