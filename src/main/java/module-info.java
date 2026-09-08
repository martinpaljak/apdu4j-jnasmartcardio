module apdu4j.jnasmartcardio {
    requires transitive java.smartcardio;
    requires com.sun.jna;
    exports jnasmartcardio;
    provides java.security.Provider with jnasmartcardio.Smartcardio;
}
