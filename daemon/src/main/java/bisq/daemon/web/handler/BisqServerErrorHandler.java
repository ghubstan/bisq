package bisq.daemon.web.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;



import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;
import ratpack.path.InvalidPathEncodingException;

public class BisqServerErrorHandler implements ServerErrorHandler {


    @Override
    public void error(Context context, Throwable throwable) throws Exception {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", throwable.getClass().getCanonicalName());
            error.put("message", throwable.getMessage());
            Gson gson = new GsonBuilder().serializeNulls().create();
            context.getResponse().status(INTERNAL_SERVER_ERROR.code()).send(gson.toJson(error));
            throw throwable;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void error(Context context, InvalidPathEncodingException exception) throws Exception {
        ServerErrorHandler.super.error(context, exception);
    }
}
