module com.uiptv {
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires org.json;
    requires org.apache.commons.io;
    requires jdk.httpserver;
    requires net.bjoernpetersen.m3u;
    requires java.sql;
    requires com.rometools.rome;
    requires annotations;
    requires javafx.web;
    requires java.net.http;

    opens com.uiptv.ui to javafx.fxml;
    exports com.uiptv.ui;
    exports com.uiptv.api;
    exports com.uiptv.util;
    exports com.uiptv.widget;
    exports com.uiptv.service;
    exports com.uiptv.model;
    opens com.uiptv.util to javafx.fxml;
}