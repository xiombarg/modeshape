/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.web.jcr.rest.client.json;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.modeshape.common.annotation.Immutable;
import org.modeshape.common.util.Base64;
import org.modeshape.web.jcr.rest.client.IJcrConstants;
import org.modeshape.web.jcr.rest.client.Utils;
import org.modeshape.web.jcr.rest.client.domain.Workspace;

/**
 * The <code>FileNode</code> class is responsible for knowing how to create a URL for a file, create a JSON representation of a
 * file, and to create the appropriate JCR nodes for a file.
 */
@Immutable
public final class FileNode extends JsonNode {

    private static final long serialVersionUID = 1L;

    // ===========================================================================================================================
    // Fields
    // ===========================================================================================================================

    /**
     * The file on the local file system.
     */
    private final File file;

    /**
     * The folder in the workspace where the file is or will be published or unpublished.
     */
    private final String path;

    /**
     * The workspace where the file is or will be published or unpublished.
     */
    private final Workspace workspace;

    // ===========================================================================================================================
    // Constructors
    // ===========================================================================================================================

    /**
     * @param workspace the workspace being used (never <code>null</code>)
     * @param path the path in the workspace (never <code>null</code>)
     * @param file the file on the local file system (never <code>null</code>)
     * @throws Exception if there is a problem constructing the file node
     */
    public FileNode( Workspace workspace,
                     String path,
                     File file ) throws Exception {
        this(workspace, path, file, false);
    }

    /**
     * @param workspace the workspace being used (never <code>null</code>)
     * @param path the path in the workspace (never <code>null</code>)
     * @param file the file on the local file system (never <code>null</code>)
     * @param versionable true if the file node should be versionable
     * @throws Exception if there is a problem constructing the file node
     */
    public FileNode( Workspace workspace,
                     String path,
                     File file,
                     boolean versionable ) throws Exception {
        super(file.getName());
        assert workspace != null;
        assert path != null;

        this.file = file;
        this.path = path;
        this.workspace = workspace;

        // add properties
        withPrimaryType(IJcrConstants.FILE_NODE_TYPE);
        if (versionable) {
            withMixin(IJcrConstants.VERSIONABLE_NODE_TYPE);
        }
        JSONObject content = createContent(file);
        withChild(IJcrConstants.CONTENT_PROPERTY, content);
    }

    @SuppressWarnings( "deprecation" )
    private JSONObject createContent( File file ) throws JSONException {
        JSONObject content = new JSONObject();

        JSONObject properties = new JSONObject();
        content.put(IJsonConstants.PROPERTIES_KEY, properties);
        properties.put(IJcrConstants.PRIMARY_TYPE_PROPERTY, IJcrConstants.RESOURCE_NODE_TYPE);

        // add required jcr:lastModified property
        Calendar lastModified = Calendar.getInstance();
        lastModified.setTimeInMillis(file.lastModified());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        properties.put(IJcrConstants.LAST_MODIFIED, formatter.format(lastModified.getTime()));

        // add required jcr:mimeType property (just use a default value)
        properties.put(IJcrConstants.MIME_TYPE, Utils.getMimeType(file));
        return content;
    }

    // ===========================================================================================================================
    // Methods
    // ===========================================================================================================================

    /**
     * @see org.modeshape.web.jcr.rest.client.json.JsonNode#getContent()
     */
    @Override
    public byte[] getContent() throws Exception {
        // add required jcr:data property (do this lazily only when the content is requested)
        JSONObject children = children();
        JSONObject kid = (JSONObject)children.get(IJcrConstants.CONTENT_PROPERTY);
        JSONObject props = (JSONObject)kid.get(IJsonConstants.PROPERTIES_KEY);
        props.put(IJcrConstants.DATA_PROPERTY, readFile());

        return super.getContent();
    }

    /**
     * Note: Currently used for testing only.
     * 
     * @param jsonResponse the JSON response obtained from performing a GET using the file content URL
     * @return the encoded file contents
     * @throws Exception if there is a problem obtaining the contents
     * @see #getFileContentsUrl()
     */
    String getFileContents( String jsonResponse ) throws Exception {
        assert jsonResponse != null;
        JSONObject contentNode = new JSONObject(jsonResponse);
        JSONObject props = (JSONObject)contentNode.get(IJsonConstants.PROPERTIES_KEY);
        return props.getString(IJcrConstants.DATA_PROPERTY);
    }

    /**
     * Note: Currently used for testing only.
     * 
     * @return the URL that can be used to obtain the encoded file contents from a workspace
     * @throws Exception if there is a problem constructing the URL
     */
    URL getFileContentsUrl() throws Exception {
        StringBuilder url = new StringBuilder(getUrl().toString());
        url.append('/').append(IJcrConstants.CONTENT_PROPERTY);
        return new URL(url.toString());
    }

    /**
     * @return the path where the file is or will be published or unpublished
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @see org.modeshape.web.jcr.rest.client.json.JsonNode#getUrl()
     */
    @Override
    public URL getUrl() throws Exception {
        FolderNode folderNode = new FolderNode(this.workspace, getPath());
        StringBuilder url = new StringBuilder(folderNode.getUrl().toString());

        // add file to path and encode the name
        url.append('/').append(JsonUtils.encode(this.file.getName()));
        return new URL(url.toString());
    }

    /**
     * @return the base 64 encoded file content
     * @throws Exception if there is a problem reading the file
     */
    String readFile() throws Exception {
        return Base64.encode(new FileInputStream(this.file.getAbsoluteFile()));
    }

}
