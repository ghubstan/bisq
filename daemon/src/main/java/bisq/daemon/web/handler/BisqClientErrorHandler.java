package bisq.daemon.web.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;



import ratpack.error.ClientErrorHandler;
import ratpack.handling.Context;

public class BisqClientErrorHandler implements ClientErrorHandler {

    @Override
    public void error(Context context, int statusCode) throws Exception {
        // When there is a problem with the request.
        Map<String, String> error = new HashMap<>();
        error.put("error", Integer.toString(statusCode));
        error.put("message", "Something not found");
        Gson gson = new GsonBuilder().serializeNulls().create();
        context.getResponse().status(statusCode).send(gson.toJson(error));
    }
}
