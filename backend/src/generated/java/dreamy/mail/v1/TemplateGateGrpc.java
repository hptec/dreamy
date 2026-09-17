package dreamy.mail.v1;

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
    comments = "Source: dreamy/mail/v1/mail.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TemplateGateGrpc {

  private TemplateGateGrpc() {}

  public static final java.lang.String SERVICE_NAME = "dreamy.mail.v1.TemplateGate";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<dreamy.mail.v1.GetEmailTemplateRequest,
      dreamy.mail.v1.GetEmailTemplateResponse> getGetEmailTemplateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetEmailTemplate",
      requestType = dreamy.mail.v1.GetEmailTemplateRequest.class,
      responseType = dreamy.mail.v1.GetEmailTemplateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.mail.v1.GetEmailTemplateRequest,
      dreamy.mail.v1.GetEmailTemplateResponse> getGetEmailTemplateMethod() {
    io.grpc.MethodDescriptor<dreamy.mail.v1.GetEmailTemplateRequest, dreamy.mail.v1.GetEmailTemplateResponse> getGetEmailTemplateMethod;
    if ((getGetEmailTemplateMethod = TemplateGateGrpc.getGetEmailTemplateMethod) == null) {
      synchronized (TemplateGateGrpc.class) {
        if ((getGetEmailTemplateMethod = TemplateGateGrpc.getGetEmailTemplateMethod) == null) {
          TemplateGateGrpc.getGetEmailTemplateMethod = getGetEmailTemplateMethod =
              io.grpc.MethodDescriptor.<dreamy.mail.v1.GetEmailTemplateRequest, dreamy.mail.v1.GetEmailTemplateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetEmailTemplate"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.mail.v1.GetEmailTemplateRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.mail.v1.GetEmailTemplateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TemplateGateMethodDescriptorSupplier("GetEmailTemplate"))
              .build();
        }
      }
    }
    return getGetEmailTemplateMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TemplateGateStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TemplateGateStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TemplateGateStub>() {
        @java.lang.Override
        public TemplateGateStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TemplateGateStub(channel, callOptions);
        }
      };
    return TemplateGateStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TemplateGateBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TemplateGateBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TemplateGateBlockingStub>() {
        @java.lang.Override
        public TemplateGateBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TemplateGateBlockingStub(channel, callOptions);
        }
      };
    return TemplateGateBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TemplateGateFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TemplateGateFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TemplateGateFutureStub>() {
        @java.lang.Override
        public TemplateGateFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TemplateGateFutureStub(channel, callOptions);
        }
      };
    return TemplateGateFutureStub.newStub(factory, channel);
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
     * 取模板(code+locale;locale 未命中服务端回退 en)。
     * </pre>
     */
    default void getEmailTemplate(dreamy.mail.v1.GetEmailTemplateRequest request,
        io.grpc.stub.StreamObserver<dreamy.mail.v1.GetEmailTemplateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetEmailTemplateMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service TemplateGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static abstract class TemplateGateImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return TemplateGateGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service TemplateGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class TemplateGateStub
      extends io.grpc.stub.AbstractAsyncStub<TemplateGateStub> {
    private TemplateGateStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TemplateGateStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TemplateGateStub(channel, callOptions);
    }

    /**
     * <pre>
     * 取模板(code+locale;locale 未命中服务端回退 en)。
     * </pre>
     */
    public void getEmailTemplate(dreamy.mail.v1.GetEmailTemplateRequest request,
        io.grpc.stub.StreamObserver<dreamy.mail.v1.GetEmailTemplateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetEmailTemplateMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service TemplateGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class TemplateGateBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<TemplateGateBlockingStub> {
    private TemplateGateBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TemplateGateBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TemplateGateBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 取模板(code+locale;locale 未命中服务端回退 en)。
     * </pre>
     */
    public dreamy.mail.v1.GetEmailTemplateResponse getEmailTemplate(dreamy.mail.v1.GetEmailTemplateRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetEmailTemplateMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service TemplateGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class TemplateGateFutureStub
      extends io.grpc.stub.AbstractFutureStub<TemplateGateFutureStub> {
    private TemplateGateFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TemplateGateFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TemplateGateFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 取模板(code+locale;locale 未命中服务端回退 en)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.mail.v1.GetEmailTemplateResponse> getEmailTemplate(
        dreamy.mail.v1.GetEmailTemplateRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetEmailTemplateMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_EMAIL_TEMPLATE = 0;

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
        case METHODID_GET_EMAIL_TEMPLATE:
          serviceImpl.getEmailTemplate((dreamy.mail.v1.GetEmailTemplateRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.mail.v1.GetEmailTemplateResponse>) responseObserver);
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
          getGetEmailTemplateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.mail.v1.GetEmailTemplateRequest,
              dreamy.mail.v1.GetEmailTemplateResponse>(
                service, METHODID_GET_EMAIL_TEMPLATE)))
        .build();
  }

  private static abstract class TemplateGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TemplateGateBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return dreamy.mail.v1.Mail.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TemplateGate");
    }
  }

  private static final class TemplateGateFileDescriptorSupplier
      extends TemplateGateBaseDescriptorSupplier {
    TemplateGateFileDescriptorSupplier() {}
  }

  private static final class TemplateGateMethodDescriptorSupplier
      extends TemplateGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    TemplateGateMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (TemplateGateGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TemplateGateFileDescriptorSupplier())
              .addMethod(getGetEmailTemplateMethod())
              .build();
        }
      }
    }
    return result;
  }
}
