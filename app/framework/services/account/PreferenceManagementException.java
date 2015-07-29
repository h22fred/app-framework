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
package framework.services.account;

/**
 * The default exception for the preference management related features
 * 
 * @author Pierre-Yves Cloux
 */
public class PreferenceManagementException extends RuntimeException {
    private static final long serialVersionUID = 6528839606889753501L;

    public PreferenceManagementException() {
    }

    public PreferenceManagementException(String message) {
        super(message);
    }

    public PreferenceManagementException(Throwable cause) {
        super(cause);
    }

    public PreferenceManagementException(String message, Throwable cause) {
        super(message, cause);
    }

}
