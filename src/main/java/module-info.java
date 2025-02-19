module com.uiptv {
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires org.json;
    requires org.apache.commons.io;
    requires jdk.httpserver;
    requires net.bjoernpetersen.m3u;
    requires com.rometools.rome;
    requires java.net.http;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;

    opens com.uiptv.ui to javafx.fxml;
    exports com.uiptv.ui;
    exports com.uiptv.api;
    exports com.uiptv.util;
    exports com.uiptv.widget;
    exports com.uiptv.service;
    exports com.uiptv.model;
    opens com.uiptv.util to javafx.fxml;
}