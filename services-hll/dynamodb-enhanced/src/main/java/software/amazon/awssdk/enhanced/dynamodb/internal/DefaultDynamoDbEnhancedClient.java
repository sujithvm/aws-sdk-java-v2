package software.amazon.awssdk.enhanced.dynamodb.internal;

import java.util.Collection;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.converter.DefaultConverterChain;
import software.amazon.awssdk.enhanced.dynamodb.converter.ItemAttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.internal.converter.ItemAttributeValueConverterChain;
import software.amazon.awssdk.enhanced.dynamodb.internal.model.DefaultTable;
import software.amazon.awssdk.enhanced.dynamodb.Table;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@SdkInternalApi
@ThreadSafe
public class DefaultDynamoDbEnhancedClient implements DynamoDbEnhancedClient {
    private boolean shouldCloseUnderlyingClient;
    private final DynamoDbClient client;
    private final ItemAttributeValueConverterChain converter;

    private DefaultDynamoDbEnhancedClient(Builder builder) {
        if (builder.client == null) {
            this.client = DynamoDbClient.create();
            this.shouldCloseUnderlyingClient = true;
        } else {
            this.client = builder.client;
            this.shouldCloseUnderlyingClient = false;
        }

        this.converter = builder.converterChain.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Table table(String tableName) {
        return DefaultTable.builder()
                           .converter(converter)
                           .dynamoDbClient(client)
                           .name(tableName)
                           .build();
    }

    @Override
    public void close() {
        if (shouldCloseUnderlyingClient) {
            client.close();
        }
    }

    @Override
    public Builder toBuilder() {
        throw new UnsupportedOperationException();
    }

    public static class Builder implements DynamoDbEnhancedClient.Builder {
        private ItemAttributeValueConverterChain.Builder converterChain =
                ItemAttributeValueConverterChain.builder()
                                                .parent(DefaultConverterChain.create());
        private DynamoDbClient client;

        private Builder() {}

        @Override
        public Builder dynamoDbClient(DynamoDbClient client) {
            this.client = client;
            return this;
        }

        @Override
        public Builder addConverters(Collection<? extends ItemAttributeValueConverter> converters) {
            converterChain.addConverters(converters);
            return this;
        }

        @Override
        public Builder addConverter(ItemAttributeValueConverter converter) {
            converterChain.addConverter(converter);
            return this;
        }

        @Override
        public Builder clearConverters() {
            converterChain.clearConverters();
            return this;
        }

        @Override
        public DynamoDbEnhancedClient build() {
            return new DefaultDynamoDbEnhancedClient(this);
        }
    }
}
