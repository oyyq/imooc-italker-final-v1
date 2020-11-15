package net.qiujuer.italker.NetInterceptor;

import android.text.TextUtils;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor {

    private static Interceptor interceptor;
    public static final String CONNECT_TIMEOUT = "CONNECT_TIMEOUT";
    public static final String READ_TIMEOUT = "READ_TIMEOUT";
    public static final String WRITE_TIMEOUT = "WRITE_TIMEOUT";

    public static Interceptor getInterceptor() {
        return interceptor;
    }


    //静态代码块初始化
    static {

        interceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {

                Request request = chain.request();
                int connectTimeout = chain.connectTimeoutMillis();
                int readTimeout = chain.readTimeoutMillis();
                int writeTimeout = chain.writeTimeoutMillis();


                String connectNew = request.header(CONNECT_TIMEOUT);
                String readNew = request.header(READ_TIMEOUT);
                String writeNew = request.header(WRITE_TIMEOUT);


                if (!TextUtils.isEmpty(connectNew)) {
                    connectTimeout = Integer.valueOf(connectNew);
                }
                if (!TextUtils.isEmpty(readNew)) {
                    readTimeout = Integer.valueOf(readNew);
                }
                if (!TextUtils.isEmpty(writeNew)) {
                    writeTimeout = Integer.valueOf(writeNew);
                }


                Request.Builder builder = request.newBuilder();
                builder.removeHeader(CONNECT_TIMEOUT);
                builder.removeHeader(READ_TIMEOUT);
                builder.removeHeader(WRITE_TIMEOUT);


                return chain
                        .withConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                        .withReadTimeout(readTimeout, TimeUnit.MILLISECONDS)
                        .withWriteTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                        .proceed(builder.build());

            }
        };

    }





}
