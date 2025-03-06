package utils;

import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import org.junit.jupiter.api.Test;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.CreateDatabaseParam;

/**
 *
 * @author yaomianxian
 * @date 2025/03/5 0005 17:54
 */
public class DatabaseTest {

    @Test
    public void add() {
        String CLUSTER_ENDPOINT = "http://localhost:19530";
        String TOKEN = "root:Milvus";

        // 1. Connect to Milvus server
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withUri(CLUSTER_ENDPOINT)
                .withToken(TOKEN)
                .build();

        MilvusServiceClient client = new MilvusServiceClient(connectParam);

        // 3. Create a new database
        CreateDatabaseParam createDatabaseParam = CreateDatabaseParam.newBuilder()
                .withDatabaseName("mydatabase")
                .build();

        R<RpcStatus> response = client.createDatabase(createDatabaseParam);
        if (response.getStatus() != 0) {
            System.out.println("Create database failed: " + response.getStatus().toString());
            return;
        } else {
            System.out.println("Create database success");
        }
    }
}
