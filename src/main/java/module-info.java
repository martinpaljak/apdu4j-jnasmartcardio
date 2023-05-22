// FIXME: including the jna-jpms dependency still generates "requires directive for an automatic module" for JAN
@SuppressWarnings("requires-automatic")
module apdu4j.jnasmartcardio {
    requires transitive java.smartcardio;
    requires com.sun.jna;
    exports jnasmartcardio;
}