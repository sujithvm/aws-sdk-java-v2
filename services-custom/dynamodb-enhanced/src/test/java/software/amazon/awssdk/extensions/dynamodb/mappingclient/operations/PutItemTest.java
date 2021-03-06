/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.extensions.dynamodb.mappingclient.operations;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.AttributeValues.numberValue;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.AttributeValues.stringValue;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItem.createUniqueFakeItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import software.amazon.awssdk.extensions.dynamodb.mappingclient.Expression;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.MapperExtension;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.OperationContext;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.TableMetadata;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.extensions.WriteModification;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItem;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItemComposedClass;

@RunWith(MockitoJUnitRunner.class)
public class PutItemTest {
    private static final String TABLE_NAME = "table-name";
    private static final OperationContext PRIMARY_CONTEXT =
        OperationContext.of(TABLE_NAME, TableMetadata.primaryIndexName());
    private static final OperationContext GSI_1_CONTEXT =
        OperationContext.of(TABLE_NAME, "gsi_1");
    private static final Expression CONDITION_EXPRESSION;
    private static final Expression CONDITION_EXPRESSION_2;

    static {
        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#test_field_1", "test_field_1");
        expressionNames.put("#test_field_2", "test_field_2");
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":test_value_1", numberValue(1));
        expressionValues.put(":test_value_2", numberValue(2));
        CONDITION_EXPRESSION = Expression.builder()
                                         .expression("#test_field_1 = :test_value_1 OR #test_field_2 = :test_value_2")
                                         .expressionNames(Collections.unmodifiableMap(expressionNames))
                                         .expressionValues(Collections.unmodifiableMap(expressionValues))
                                         .build();
    }

    static {
        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#test_field_3", "test_field_3");
        expressionNames.put("#test_field_4", "test_field_4");
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":test_value_3", numberValue(3));
        expressionValues.put(":test_value_4", numberValue(4));
        CONDITION_EXPRESSION_2 = Expression.builder()
                                         .expression("#test_field_3 = :test_value_3 OR #test_field_4 = :test_value_4")
                                         .expressionNames(Collections.unmodifiableMap(expressionNames))
                                         .expressionValues(Collections.unmodifiableMap(expressionValues))
                                         .build();
    }

    @Mock
    private DynamoDbClient mockDynamoDbClient;
    @Mock
    private MapperExtension mockMapperExtension;

    @Test
    public void getServiceCall_makesTheRightCallAndReturnsResponse() {
        FakeItem keyItem = createUniqueFakeItem();
        PutItem<FakeItem> putItemOperation = PutItem.of(keyItem);
        PutItemRequest getItemRequest = PutItemRequest.builder().tableName(TABLE_NAME).build();
        PutItemResponse expectedResponse = PutItemResponse.builder().build();
        when(mockDynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(expectedResponse);

        PutItemResponse response = putItemOperation.serviceCall(mockDynamoDbClient).apply(getItemRequest);

        assertThat(response, sameInstance(expectedResponse));
        verify(mockDynamoDbClient).putItem(getItemRequest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateRequest_withIndex_throwsIllegalArgumentException() {
        FakeItem item = createUniqueFakeItem();
        PutItem<FakeItem> putItemOperation = PutItem.of(item);

        putItemOperation.generateRequest(FakeItem.getTableSchema(), GSI_1_CONTEXT, null);
    }

    @Test
    public void generateRequest_generatesCorrectRequest() {
        FakeItem fakeItem = createUniqueFakeItem();
        fakeItem.setSubclassAttribute("subclass-value");
        PutItem<FakeItem> putItemOperation = PutItem.of(fakeItem);

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  null);

        Map<String, AttributeValue> expectedItemMap = new HashMap<>();
        expectedItemMap.put("id", AttributeValue.builder().s(fakeItem.getId()).build());
        expectedItemMap.put("subclass_attribute", AttributeValue.builder().s("subclass-value").build());
        PutItemRequest expectedRequest = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(expectedItemMap)
            .build();
        assertThat(request, is(expectedRequest));
    }

    @Test
    public void generateRequest_withConditionExpression_generatesCorrectRequest() {
        FakeItem fakeItem = createUniqueFakeItem();
        fakeItem.setSubclassAttribute("subclass-value");

        PutItem<FakeItem> putItemOperation = PutItem.builder()
                                                    .conditionExpression(CONDITION_EXPRESSION)
                                                    .item(fakeItem)
                                                    .build();

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  null);

        Map<String, AttributeValue> expectedItemMap = new HashMap<>();
        expectedItemMap.put("id", AttributeValue.builder().s(fakeItem.getId()).build());
        expectedItemMap.put("subclass_attribute", AttributeValue.builder().s("subclass-value").build());
        PutItemRequest expectedRequest =
            PutItemRequest.builder()
                          .tableName(TABLE_NAME)
                          .item(expectedItemMap)
                          .conditionExpression(CONDITION_EXPRESSION.expression())
                          .expressionAttributeNames(CONDITION_EXPRESSION.expressionNames())
                          .expressionAttributeValues(CONDITION_EXPRESSION.expressionValues())
                          .build();
        assertThat(request, is(expectedRequest));
    }

    @Test
    public void generateRequest_withConditionExpression_andExtensionWithSingleCondition() {
        FakeItem baseFakeItem = createUniqueFakeItem();
        when(mockMapperExtension.beforeWrite(anyMap(), any(), any()))
            .thenReturn(WriteModification.builder().additionalConditionalExpression(CONDITION_EXPRESSION_2).build());
        PutItem<FakeItem> putItemOperation =
            PutItem.builder().item(baseFakeItem).conditionExpression(CONDITION_EXPRESSION).build();

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  mockMapperExtension);

        Expression expectedCondition = Expression.coalesce(CONDITION_EXPRESSION, CONDITION_EXPRESSION_2, " AND ");
        assertThat(request.conditionExpression(), is(expectedCondition.expression()));
        assertThat(request.expressionAttributeNames(), is(expectedCondition.expressionNames()));
        assertThat(request.expressionAttributeValues(), is(expectedCondition.expressionValues()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateRequest_noPartitionKey_throwsIllegalArgumentException() {
        FakeItemComposedClass keyItem = FakeItemComposedClass.builder().composedAttribute("whatever").build();
        PutItem<FakeItemComposedClass> putItemOperation = PutItem.of(keyItem);

        putItemOperation.generateRequest(FakeItemComposedClass.getTableSchema(), PRIMARY_CONTEXT, null);
    }

    @Test
    public void transformResponse_doesNotBlowUp() {
        FakeItem fakeItem = createUniqueFakeItem();
        PutItem<FakeItem> putItemOperation = PutItem.of(fakeItem);
        PutItemResponse response = PutItemResponse.builder()
                                                  .build();

        putItemOperation.transformResponse(response, FakeItem.getTableSchema(), PRIMARY_CONTEXT, null);
    }

    @Test
    public void generateRequest_withExtension_modifiesItemToPut() {
        FakeItem baseFakeItem = createUniqueFakeItem();
        FakeItem fakeItem = createUniqueFakeItem();
        Map<String, AttributeValue> baseMap = FakeItem.getTableSchema().itemToMap(baseFakeItem, true);
        Map<String, AttributeValue> fakeMap = FakeItem.getTableSchema().itemToMap(fakeItem, true);
        when(mockMapperExtension.beforeWrite(anyMap(), any(), any()))
            .thenReturn(WriteModification.builder().transformedItem(fakeMap).build());
        PutItem<FakeItem> putItemOperation = PutItem.of(baseFakeItem);

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  mockMapperExtension);

        assertThat(request.item(), is(fakeMap));
        verify(mockMapperExtension).beforeWrite(baseMap, PRIMARY_CONTEXT, FakeItem.getTableMetadata());
    }

    @Test
    public void generateRequest_withExtension_singleCondition() {
        FakeItem baseFakeItem = createUniqueFakeItem();
        FakeItem fakeItem = createUniqueFakeItem();
        Map<String, AttributeValue> fakeMap = FakeItem.getTableSchema().itemToMap(fakeItem, true);
        Expression condition = Expression.builder().expression("condition").expressionValues(fakeMap).build();
        when(mockMapperExtension.beforeWrite(anyMap(), any(), any()))
            .thenReturn(WriteModification.builder().additionalConditionalExpression(condition).build());
        PutItem<FakeItem> putItemOperation = PutItem.of(baseFakeItem);

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  mockMapperExtension);

        assertThat(request.conditionExpression(), is("condition"));
        assertThat(request.expressionAttributeValues(), is(fakeMap));
    }

    @Test
    public void generateRequest_withExtension_noModifications() {
        FakeItem baseFakeItem = createUniqueFakeItem();
        when(mockMapperExtension.beforeWrite(anyMap(), any(), any()))
            .thenReturn(WriteModification.builder().build());
        PutItem<FakeItem> putItemOperation = PutItem.of(baseFakeItem);

        PutItemRequest request = putItemOperation.generateRequest(FakeItem.getTableSchema(),
                                                                  PRIMARY_CONTEXT,
                                                                  mockMapperExtension);
        assertThat(request.conditionExpression(), is(nullValue()));
        assertThat(request.expressionAttributeValues().size(), is(0));
    }

    @Test
    public void generateTransactWriteItem_basicRequest() {
        FakeItem fakeItem = createUniqueFakeItem();
        Map<String, AttributeValue> fakeItemMap = FakeItem.getTableSchema().itemToMap(fakeItem, true);
        PutItem<FakeItem> putItemOperation = spy(PutItem.of(fakeItem));
        OperationContext context = OperationContext.of(TABLE_NAME, TableMetadata.primaryIndexName());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                                                      .tableName(TABLE_NAME)
                                                      .item(fakeItemMap)
                                                      .build();
        doReturn(putItemRequest).when(putItemOperation).generateRequest(any(), any(), any());

        TransactWriteItem actualResult = putItemOperation.generateTransactWriteItem(FakeItem.getTableSchema(),
                                                                                    context,
                                                                                    mockMapperExtension);

        TransactWriteItem expectedResult = TransactWriteItem.builder()
                                                            .put(Put.builder()
                                                                    .item(fakeItemMap)
                                                                    .tableName(TABLE_NAME)
                                                                    .build())
                                                            .build();
        assertThat(actualResult, is(expectedResult));
        verify(putItemOperation).generateRequest(FakeItem.getTableSchema(), context, mockMapperExtension);
    }

    @Test
    public void generateTransactWriteItem_conditionalRequest() {
        FakeItem fakeItem = createUniqueFakeItem();
        Map<String, AttributeValue> fakeItemMap = FakeItem.getTableSchema().itemToMap(fakeItem, true);
        PutItem<FakeItem> putItemOperation = spy(PutItem.of(fakeItem));
        OperationContext context = OperationContext.of(TABLE_NAME, TableMetadata.primaryIndexName());

        String conditionExpression = "condition-expression";
        Map<String, AttributeValue> attributeValues = Collections.singletonMap("key", stringValue("value1"));
        Map<String, String> attributeNames = Collections.singletonMap("key", "value2");

        PutItemRequest putItemRequest = PutItemRequest.builder()
                                                      .tableName(TABLE_NAME)
                                                      .item(fakeItemMap)
                                                      .conditionExpression(conditionExpression)
                                                      .expressionAttributeValues(attributeValues)
                                                      .expressionAttributeNames(attributeNames)
                                                      .build();
        doReturn(putItemRequest).when(putItemOperation).generateRequest(any(), any(), any());

        TransactWriteItem actualResult = putItemOperation.generateTransactWriteItem(FakeItem.getTableSchema(),
                                                                                    context,
                                                                                    mockMapperExtension);

        TransactWriteItem expectedResult = TransactWriteItem.builder()
                                                            .put(Put.builder()
                                                                    .item(fakeItemMap)
                                                                    .tableName(TABLE_NAME)
                                                                    .conditionExpression(conditionExpression)
                                                                    .expressionAttributeNames(attributeNames)
                                                                    .expressionAttributeValues(attributeValues)
                                                                    .build())
                                                            .build();
        assertThat(actualResult, is(expectedResult));
        verify(putItemOperation).generateRequest(FakeItem.getTableSchema(), context, mockMapperExtension);
    }
}