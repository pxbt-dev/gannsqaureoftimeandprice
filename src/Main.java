public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;  // Default fallback

        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                port = Integer.parseInt(envPort);
                System.out.println("Using PORT from environment: " + port);
            } catch (NumberFormatException e) {
                System.out.println("Invalid PORT environment variable: " + envPort);
            }
        }

        else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                System.out.println("Using port from arguments: " + port);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default: 8080");
            }
        }

        System.out.println("Starting HTTP server on port: " + port);
        HttpApiServer server = new HttpApiServer(port);
        server.start();
    }
}