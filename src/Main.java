public static void main(String[] args) throws Exception {
    int port = 8080;
    if (args.length > 0) {
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Usage: java Main [port]");
            System.out.println("Default port: 8080");
        }
    }

    HttpApiServer server = new HttpApiServer(port);
    server.start();
}