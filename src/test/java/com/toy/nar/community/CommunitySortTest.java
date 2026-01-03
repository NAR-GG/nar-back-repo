package com.toy.nar.community;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CommunitySortTest {

    @Test
    public void testSort() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("test_result.log"))) {
            testOpggPopular(writer);
            writer.write("--------------------------------------------------\n");
            testInvenPopular(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void testOpggPopular(BufferedWriter writer) throws IOException {
        writer.write("[OP.GG Popular Sort Test]\n");
        // 인기순 정렬 파라미터 추정: ?sort=popular
        String url = "https://talk.op.gg/s/lol/esports?sort=popular"; 
        
        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get();

            String jsonData = doc.getElementById("__NEXT_DATA__").data();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonData);
            JsonNode posts = root.path("props").path("pageProps").path("posts").path("data");

            if (posts.isArray() && posts.size() > 0) {
                writer.write("Fetched " + posts.size() + " posts.\n");
                for (int i = 0; i < Math.min(5, posts.size()); i++) {
                    JsonNode node = posts.get(i);
                    writer.write(String.format("[%d] Vote: %d, Title: %s%n", 
                        i + 1, node.path("vote_score").asInt(), node.path("title").asText()));
                }
            } else {
                writer.write("No posts found or format changed.\n");
            }
        } catch (Exception e) {
            writer.write("OP.GG Test Failed: " + e.getMessage() + "\n");
        }
    }

    private void testInvenPopular(BufferedWriter writer) throws IOException {
        writer.write("[Inven Popular Sort Test]\n");
        // 인벤 "화제글(3추)" URL 패턴 추정 (모바일)
        String url = "https://m.inven.co.kr/board/lol/4625?my=chuchon";

        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .get();

            Elements posts = doc.select("section.mo-board-list li.list");
            writer.write("Fetched " + posts.size() + " posts.\n");

            int count = 0;
            for (org.jsoup.nodes.Element post : posts) {
                if (count++ >= 5) break;
                String title = post.select("span.subject").text();
                String recoText = post.select("span.reco").text();
                writer.write(String.format("[%d] Vote: %s, Title: %s%n", count, recoText, title));
            }
        } catch (Exception e) {
            writer.write("Inven Test Failed: " + e.getMessage() + "\n");
        }
    }
}
