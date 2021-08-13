
package org.saturn.app.service;

import java.sql.Connection;

public interface DataBaseConnection {
    Connection getConnection();
}