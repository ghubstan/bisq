package bisq.core.grpc;

import static java.lang.String.format;

class CoreHelpService {

    private final String help = new StringBuilder("Bisq RPC Client")
            .append("\n")
            .append(format("Usage: bisq-cli [options] <method> [params]"))
            .append("\n\n")
            .append(format("%-19s%-30s%s%n", "Method", "Params", "Description"))
            .append(format("%-19s%-30s%s%n", "------", "------", "------------"))
            .append(format("%-19s%-30s%s%n", "getversion", "", "Get server version"))
            .append(format("%-19s%-30s%s%n", "getbalance", "", "Get server wallet balance"))
            .append(format("%-19s%-30s%s%n", "lockwallet", "", "Remove wallet password from memory, locking the wallet"))
            .append(format("%-19s%-30s%s%n", "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds"))
            .append(format("%-19s%-30s%s%n", "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet"))
            .append("\n")
            .toString();

    public String getHelp() {
        return this.help;
    }
    
    public String getHelp(Method method) {
        // TODO return detailed help for method
        return this.help;
    }
}
