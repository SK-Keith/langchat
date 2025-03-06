package utils;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.v2.common.DataType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import org.junit.jupiter.api.Test;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.common.IndexParam;
import java.util.*;

/**
 *
 * @author yaomianxian
 * @date 2025/03/5 0005 15:29
 */
public class CollectionTest {

    @Test
    public void list() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://localhost:19530")
                .token("root:Milvus")
                .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        ListCollectionsResp resp = client.listCollections();
        System.out.println("----------------------------------------------");
        System.out.println(resp.getCollectionNames());

    }

    @Test
    public void add() throws InterruptedException {
        String CLUSTER_ENDPOINT = "http://localhost:19530";
        String TOKEN = "root:Milvus";
        String collectionName = "items";


        // 1. Connect to Milvus server
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(CLUSTER_ENDPOINT)
                .token(TOKEN)
//                .dbName("mydatabase")
                .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        // 检查并删除已存在的集合
        if (client.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build())) {
            client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
        }

        // 3. Create a collection in customized setup mode
        // 3.1 Create schema
        CreateCollectionReq.CollectionSchema schema = client.createSchema();

        // 3.2 Add fields to schema
        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.VarChar)  // 定义为字符串类型！！
                .isPrimaryKey(true)
                .autoID(false)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(1024)  // 必须指定向量维度（如128/768等）
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("embedding_id")
                .dataType(DataType.Int64)
                .build());

// 3.3 Prepare index parameters
        IndexParam indexParamForIdField = IndexParam.builder()
                .fieldName("id")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .build();

        IndexParam indexParamForVectorField = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(indexParamForIdField);
        indexParams.add(indexParamForVectorField);



        // 3.4 Create a collection with schema and index parameters
        CreateCollectionReq customizedSetupReq1 = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();

        client.createCollection(customizedSetupReq1);

        client.loadCollection(
                LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build()
        );
        Thread.sleep(1000);

        // 再次检查加载状态
        Boolean loaded = client.getLoadState(GetLoadStateReq.builder()
                .collectionName(collectionName)
                .build());
        System.out.println(loaded);  // 现在应输出 true

// Output:
// true


    }

    @Test
    public void add2() {
        String CLUSTER_ENDPOINT = "http://localhost:19530";
        String TOKEN = "root:Milvus";


        // 1. Connect to Milvus server
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(CLUSTER_ENDPOINT)
                .dbName("mydatabase")
                .token(TOKEN)
                .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        // 2. Create a collection in quick setup mode
        CreateCollectionReq quickSetupReq = CreateCollectionReq.builder()
                .collectionName("items")
                .dimension(1024)
                .build();

        client.createCollection(quickSetupReq);
        // 检查是否存在
        GetLoadStateReq quickSetupLoadStateReq = GetLoadStateReq.builder()
                .collectionName("items")
                .build();

        Boolean res = client.getLoadState(quickSetupLoadStateReq);
        System.out.println(res);

// Output:
// true
    }

    @Test
    public void delete() {
        String CLUSTER_ENDPOINT = "http://localhost:19530";
        String TOKEN = "root:Milvus";


        // 1. Connect to Milvus server
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(CLUSTER_ENDPOINT)
                .dbName("mydatabase")
                .token(TOKEN)
                .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        // 8. Release the collection
        ReleaseCollectionReq releaseCollectionReq = ReleaseCollectionReq.builder()
                .collectionName("items")
                .build();

        client.releaseCollection(releaseCollectionReq);

        GetLoadStateReq loadStateReq = GetLoadStateReq.builder()
                .collectionName("items")
                .build();
        Boolean res = client.getLoadState(loadStateReq);
        System.out.println(res);

// Output:
// false
    }

}
