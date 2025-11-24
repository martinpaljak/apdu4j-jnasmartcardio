module apdu4j.jnasmartcardio {
    requires transitive java.smartcardio;
    requires com.sun.jna;
    exports jnasmartcardio;
}
