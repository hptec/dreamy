package dreamy.audit.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * ----------------------------------------------------------------------------
 * 服务定义
 * ----------------------------------------------------------------------------
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.66.0)",
    comments = "Source: dreamy/audit/v1/audit.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AuditGateGrpc {

  private AuditGateGrpc() {}

  public static final java.lang.String SERVICE_NAME = "dreamy.audit.v1.AuditGate";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<dreamy.audit.v1.RecordOperationLogRequest,
      dreamy.audit.v1.RecordOperationLogResponse> getRecordOperationLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RecordOperationLog",
      requestType = dreamy.audit.v1.RecordOperationLogRequest.class,
      responseType = dreamy.audit.v1.RecordOperationLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.audit.v1.RecordOperationLogRequest,
      dreamy.audit.v1.RecordOperationLogResponse> getRecordOperationLogMethod() {
    io.grpc.MethodDescriptor<dreamy.audit.v1.RecordOperationLogRequest, dreamy.audit.v1.RecordOperationLogResponse> getRecordOperationLogMethod;
    if ((getRecordOperationLogMethod = AuditGateGrpc.getRecordOperationLogMethod) == null) {
      synchronized (AuditGateGrpc.class) {
        if ((getRecordOperationLogMethod = AuditGateGrpc.getRecordOperationLogMethod) == null) {
          AuditGateGrpc.getRecordOperationLogMethod = getRecordOperationLogMethod =
              io.grpc.MethodDescriptor.<dreamy.audit.v1.RecordOperationLogRequest, dreamy.audit.v1.RecordOperationLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RecordOperationLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.RecordOperationLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.RecordOperationLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuditGateMethodDescriptorSupplier("RecordOperationLog"))
              .build();
        }
      }
    }
    return getRecordOperationLogMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.audit.v1.ListOperationLogsRequest,
      dreamy.audit.v1.ListOperationLogsResponse> getListOperationLogsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListOperationLogs",
      requestType = dreamy.audit.v1.ListOperationLogsRequest.class,
      responseType = dreamy.audit.v1.ListOperationLogsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.audit.v1.ListOperationLogsRequest,
      dreamy.audit.v1.ListOperationLogsResponse> getListOperationLogsMethod() {
    io.grpc.MethodDescriptor<dreamy.audit.v1.ListOperationLogsRequest, dreamy.audit.v1.ListOperationLogsResponse> getListOperationLogsMethod;
    if ((getListOperationLogsMethod = AuditGateGrpc.getListOperationLogsMethod) == null) {
      synchronized (AuditGateGrpc.class) {
        if ((getListOperationLogsMethod = AuditGateGrpc.getListOperationLogsMethod) == null) {
          AuditGateGrpc.getListOperationLogsMethod = getListOperationLogsMethod =
              io.grpc.MethodDescriptor.<dreamy.audit.v1.ListOperationLogsRequest, dreamy.audit.v1.ListOperationLogsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListOperationLogs"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.ListOperationLogsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.ListOperationLogsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AuditGateMethodDescriptorSupplier("ListOperationLogs"))
              .build();
        }
      }
    }
    return getListOperationLogsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.audit.v1.StreamOperationLogsRequest,
      dreamy.audit.v1.OperationLogRow> getStreamOperationLogsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamOperationLogs",
      requestType = dreamy.audit.v1.StreamOperationLogsRequest.class,
      responseType = dreamy.audit.v1.OperationLogRow.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<dreamy.audit.v1.StreamOperationLogsRequest,
      dreamy.audit.v1.OperationLogRow> getStreamOperationLogsMethod() {
    io.grpc.MethodDescriptor<dreamy.audit.v1.StreamOperationLogsRequest, dreamy.audit.v1.OperationLogRow> getStreamOperationLogsMethod;
    if ((getStreamOperationLogsMethod = AuditGateGrpc.getStreamOperationLogsMethod) == null) {
      synchronized (AuditGateGrpc.class) {
        if ((getStreamOperationLogsMethod = AuditGateGrpc.getStreamOperationLogsMethod) == null) {
          AuditGateGrpc.getStreamOperationLogsMethod = getStreamOperationLogsMethod =
              io.grpc.MethodDescriptor.<dreamy.audit.v1.StreamOperationLogsRequest, dreamy.audit.v1.OperationLogRow>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamOperationLogs"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.StreamOperationLogsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.audit.v1.OperationLogRow.getDefaultInstance()))
              .setSchemaDescriptor(new AuditGateMethodDescriptorSupplier("StreamOperationLogs"))
              .build();
        }
      }
    }
    return getStreamOperationLogsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AuditGateStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuditGateStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuditGateStub>() {
        @java.lang.Override
        public AuditGateStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuditGateStub(channel, callOptions);
        }
      };
    return AuditGateStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AuditGateBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuditGateBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuditGateBlockingStub>() {
        @java.lang.Override
        public AuditGateBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuditGateBlockingStub(channel, callOptions);
        }
      };
    return AuditGateBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AuditGateFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AuditGateFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AuditGateFutureStub>() {
        @java.lang.Override
        public AuditGateFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AuditGateFutureStub(channel, callOptions);
        }
      };
    return AuditGateFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * 审计写入(best-effort:调用方失败吞错,不重试不阻塞)。
     * </pre>
     */
    default void recordOperationLog(dreamy.audit.v1.RecordOperationLogRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.RecordOperationLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRecordOperationLogMethod(), responseObserver);
    }

    /**
     * <pre>
     * 分页查询(admin operation-logs 列表;过滤参数语义与 REST 端点一致)。
     * </pre>
     */
    default void listOperationLogs(dreamy.audit.v1.ListOperationLogsRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.ListOperationLogsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListOperationLogsMethod(), responseObserver);
    }

    /**
     * <pre>
     * 导出流(CSV 数据源;from/to 必传,跨度 ≤92 天由调用方校验;ORDER BY id ASC)。
     * </pre>
     */
    default void streamOperationLogs(dreamy.audit.v1.StreamOperationLogsRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.OperationLogRow> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStreamOperationLogsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AuditGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static abstract class AuditGateImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AuditGateGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AuditGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class AuditGateStub
      extends io.grpc.stub.AbstractAsyncStub<AuditGateStub> {
    private AuditGateStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuditGateStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuditGateStub(channel, callOptions);
    }

    /**
     * <pre>
     * 审计写入(best-effort:调用方失败吞错,不重试不阻塞)。
     * </pre>
     */
    public void recordOperationLog(dreamy.audit.v1.RecordOperationLogRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.RecordOperationLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRecordOperationLogMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 分页查询(admin operation-logs 列表;过滤参数语义与 REST 端点一致)。
     * </pre>
     */
    public void listOperationLogs(dreamy.audit.v1.ListOperationLogsRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.ListOperationLogsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListOperationLogsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 导出流(CSV 数据源;from/to 必传,跨度 ≤92 天由调用方校验;ORDER BY id ASC)。
     * </pre>
     */
    public void streamOperationLogs(dreamy.audit.v1.StreamOperationLogsRequest request,
        io.grpc.stub.StreamObserver<dreamy.audit.v1.OperationLogRow> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getStreamOperationLogsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AuditGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class AuditGateBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AuditGateBlockingStub> {
    private AuditGateBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuditGateBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuditGateBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 审计写入(best-effort:调用方失败吞错,不重试不阻塞)。
     * </pre>
     */
    public dreamy.audit.v1.RecordOperationLogResponse recordOperationLog(dreamy.audit.v1.RecordOperationLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRecordOperationLogMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 分页查询(admin operation-logs 列表;过滤参数语义与 REST 端点一致)。
     * </pre>
     */
    public dreamy.audit.v1.ListOperationLogsResponse listOperationLogs(dreamy.audit.v1.ListOperationLogsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListOperationLogsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 导出流(CSV 数据源;from/to 必传,跨度 ≤92 天由调用方校验;ORDER BY id ASC)。
     * </pre>
     */
    public java.util.Iterator<dreamy.audit.v1.OperationLogRow> streamOperationLogs(
        dreamy.audit.v1.StreamOperationLogsRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getStreamOperationLogsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AuditGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class AuditGateFutureStub
      extends io.grpc.stub.AbstractFutureStub<AuditGateFutureStub> {
    private AuditGateFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AuditGateFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AuditGateFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 审计写入(best-effort:调用方失败吞错,不重试不阻塞)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.audit.v1.RecordOperationLogResponse> recordOperationLog(
        dreamy.audit.v1.RecordOperationLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRecordOperationLogMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 分页查询(admin operation-logs 列表;过滤参数语义与 REST 端点一致)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.audit.v1.ListOperationLogsResponse> listOperationLogs(
        dreamy.audit.v1.ListOperationLogsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListOperationLogsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_RECORD_OPERATION_LOG = 0;
  private static final int METHODID_LIST_OPERATION_LOGS = 1;
  private static final int METHODID_STREAM_OPERATION_LOGS = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_RECORD_OPERATION_LOG:
          serviceImpl.recordOperationLog((dreamy.audit.v1.RecordOperationLogRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.audit.v1.RecordOperationLogResponse>) responseObserver);
          break;
        case METHODID_LIST_OPERATION_LOGS:
          serviceImpl.listOperationLogs((dreamy.audit.v1.ListOperationLogsRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.audit.v1.ListOperationLogsResponse>) responseObserver);
          break;
        case METHODID_STREAM_OPERATION_LOGS:
          serviceImpl.streamOperationLogs((dreamy.audit.v1.StreamOperationLogsRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.audit.v1.OperationLogRow>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRecordOperationLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.audit.v1.RecordOperationLogRequest,
              dreamy.audit.v1.RecordOperationLogResponse>(
                service, METHODID_RECORD_OPERATION_LOG)))
        .addMethod(
          getListOperationLogsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.audit.v1.ListOperationLogsRequest,
              dreamy.audit.v1.ListOperationLogsResponse>(
                service, METHODID_LIST_OPERATION_LOGS)))
        .addMethod(
          getStreamOperationLogsMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              dreamy.audit.v1.StreamOperationLogsRequest,
              dreamy.audit.v1.OperationLogRow>(
                service, METHODID_STREAM_OPERATION_LOGS)))
        .build();
  }

  private static abstract class AuditGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AuditGateBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return dreamy.audit.v1.Audit.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AuditGate");
    }
  }

  private static final class AuditGateFileDescriptorSupplier
      extends AuditGateBaseDescriptorSupplier {
    AuditGateFileDescriptorSupplier() {}
  }

  private static final class AuditGateMethodDescriptorSupplier
      extends AuditGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AuditGateMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AuditGateGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AuditGateFileDescriptorSupplier())
              .addMethod(getRecordOperationLogMethod())
              .addMethod(getListOperationLogsMethod())
              .addMethod(getStreamOperationLogsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
