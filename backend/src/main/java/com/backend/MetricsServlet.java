package com.backend;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.util.Map;

public class MetricsServlet extends HttpServlet {

    private ElasticsearchClient esClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init() throws ServletException{
        RestClient restClient = RestClient.builder(new HttpHost("elasticsearch", 9200)).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        esClient = new ElasticsearchClient(transport);
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            SearchResponse<Map> searchResponse = esClient.search(s -> s
                .index("telemetry-metrics")
                .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Desc)))
                .size(1),
                Map.class
            );

            if (!searchResponse.hits().hits().isEmpty()) {
                Map<String, Object> latestMetric = searchResponse.hits().hits().get(0).source();
                String jsonResponse = objectMapper.writeValueAsString(latestMetric);
                resp.getWriter().write(jsonResponse);
                System.out.println(jsonResponse);
            } else {
                System.out.println("No se ha podido enviar el paquete");
                resp.getWriter().write("{}");
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"Fallo de conexión o consulta a Elasticsearch\"}");
        }
    }
}