package searchengine.util;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupConnection;
import searchengine.dto.PageInfo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HtmlParser {

    private final JsoupConnection jsoupConnection;
    private final Random random = new Random();


    public PageInfo getPageInfo(String url) throws IOException, InterruptedException {

        Connection.Response response = getResponse(url);
        return new PageInfo(response.parse().html(), response.statusCode());
    }



    public Set<String> getPaths(String content){
        Document document = Jsoup.parse(content);

        return document.select("a[href]").stream()
                .map(element -> element.attr("href"))
                .filter(path -> path.startsWith("/"))
                .collect(Collectors.toSet());
    }
    private Connection.Response getResponse(String url) throws IOException, InterruptedException {
        Thread.sleep(jsoupConnection.getTimeoutMin() + Math.abs(random.nextInt()) %
                jsoupConnection.getTimeoutMax() - jsoupConnection.getTimeoutMin());

        return Jsoup.connect(url)
                .maxBodySize(0)
                .userAgent(jsoupConnection.getUserAgent())
                .referrer(jsoupConnection.getReferrer())
                .header("Accept-Language", "ru")
                .ignoreHttpErrors(true)
                .sslSocketFactory(socketFactory())
                .execute();
    }

    private SSLSocketFactory socketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create a SSL socket factory", e);
        }


    }

    public String htmlToText(String content){

        return Jsoup.parse(content).text();

    }

    public String getTitle(String content){
        Document document = Jsoup.parse(content);
        return document.title();
    }

}
