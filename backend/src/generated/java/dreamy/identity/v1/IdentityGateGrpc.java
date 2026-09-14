package dreamy.identity.v1;

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
    comments = "Source: dreamy/identity/v1/identity.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class IdentityGateGrpc {

  private IdentityGateGrpc() {}

  public static final java.lang.String SERVICE_NAME = "dreamy.identity.v1.IdentityGate";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateStoreSessionRequest,
      dreamy.identity.v1.ValidateStoreSessionResponse> getValidateStoreSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ValidateStoreSession",
      requestType = dreamy.identity.v1.ValidateStoreSessionRequest.class,
      responseType = dreamy.identity.v1.ValidateStoreSessionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateStoreSessionRequest,
      dreamy.identity.v1.ValidateStoreSessionResponse> getValidateStoreSessionMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateStoreSessionRequest, dreamy.identity.v1.ValidateStoreSessionResponse> getValidateStoreSessionMethod;
    if ((getValidateStoreSessionMethod = IdentityGateGrpc.getValidateStoreSessionMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getValidateStoreSessionMethod = IdentityGateGrpc.getValidateStoreSessionMethod) == null) {
          IdentityGateGrpc.getValidateStoreSessionMethod = getValidateStoreSessionMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.ValidateStoreSessionRequest, dreamy.identity.v1.ValidateStoreSessionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ValidateStoreSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ValidateStoreSessionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ValidateStoreSessionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("ValidateStoreSession"))
              .build();
        }
      }
    }
    return getValidateStoreSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateAdminSessionRequest,
      dreamy.identity.v1.ValidateAdminSessionResponse> getValidateAdminSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ValidateAdminSession",
      requestType = dreamy.identity.v1.ValidateAdminSessionRequest.class,
      responseType = dreamy.identity.v1.ValidateAdminSessionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateAdminSessionRequest,
      dreamy.identity.v1.ValidateAdminSessionResponse> getValidateAdminSessionMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.ValidateAdminSessionRequest, dreamy.identity.v1.ValidateAdminSessionResponse> getValidateAdminSessionMethod;
    if ((getValidateAdminSessionMethod = IdentityGateGrpc.getValidateAdminSessionMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getValidateAdminSessionMethod = IdentityGateGrpc.getValidateAdminSessionMethod) == null) {
          IdentityGateGrpc.getValidateAdminSessionMethod = getValidateAdminSessionMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.ValidateAdminSessionRequest, dreamy.identity.v1.ValidateAdminSessionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ValidateAdminSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ValidateAdminSessionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ValidateAdminSessionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("ValidateAdminSession"))
              .build();
        }
      }
    }
    return getValidateAdminSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.ResolvePermissionsRequest,
      dreamy.identity.v1.ResolvePermissionsResponse> getResolvePermissionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ResolvePermissions",
      requestType = dreamy.identity.v1.ResolvePermissionsRequest.class,
      responseType = dreamy.identity.v1.ResolvePermissionsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.ResolvePermissionsRequest,
      dreamy.identity.v1.ResolvePermissionsResponse> getResolvePermissionsMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.ResolvePermissionsRequest, dreamy.identity.v1.ResolvePermissionsResponse> getResolvePermissionsMethod;
    if ((getResolvePermissionsMethod = IdentityGateGrpc.getResolvePermissionsMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getResolvePermissionsMethod = IdentityGateGrpc.getResolvePermissionsMethod) == null) {
          IdentityGateGrpc.getResolvePermissionsMethod = getResolvePermissionsMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.ResolvePermissionsRequest, dreamy.identity.v1.ResolvePermissionsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ResolvePermissions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ResolvePermissionsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ResolvePermissionsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("ResolvePermissions"))
              .build();
        }
      }
    }
    return getResolvePermissionsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.GetUserRequest,
      dreamy.identity.v1.GetUserResponse> getGetUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetUser",
      requestType = dreamy.identity.v1.GetUserRequest.class,
      responseType = dreamy.identity.v1.GetUserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.GetUserRequest,
      dreamy.identity.v1.GetUserResponse> getGetUserMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.GetUserRequest, dreamy.identity.v1.GetUserResponse> getGetUserMethod;
    if ((getGetUserMethod = IdentityGateGrpc.getGetUserMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getGetUserMethod = IdentityGateGrpc.getGetUserMethod) == null) {
          IdentityGateGrpc.getGetUserMethod = getGetUserMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.GetUserRequest, dreamy.identity.v1.GetUserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.GetUserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.GetUserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("GetUser"))
              .build();
        }
      }
    }
    return getGetUserMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.ListUsersRequest,
      dreamy.identity.v1.ListUsersResponse> getListUsersMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListUsers",
      requestType = dreamy.identity.v1.ListUsersRequest.class,
      responseType = dreamy.identity.v1.ListUsersResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.ListUsersRequest,
      dreamy.identity.v1.ListUsersResponse> getListUsersMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.ListUsersRequest, dreamy.identity.v1.ListUsersResponse> getListUsersMethod;
    if ((getListUsersMethod = IdentityGateGrpc.getListUsersMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getListUsersMethod = IdentityGateGrpc.getListUsersMethod) == null) {
          IdentityGateGrpc.getListUsersMethod = getListUsersMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.ListUsersRequest, dreamy.identity.v1.ListUsersResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListUsers"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ListUsersRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.ListUsersResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("ListUsers"))
              .build();
        }
      }
    }
    return getListUsersMethod;
  }

  private static volatile io.grpc.MethodDescriptor<dreamy.identity.v1.EnsureDemoUserRequest,
      dreamy.identity.v1.EnsureDemoUserResponse> getEnsureDemoUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "EnsureDemoUser",
      requestType = dreamy.identity.v1.EnsureDemoUserRequest.class,
      responseType = dreamy.identity.v1.EnsureDemoUserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<dreamy.identity.v1.EnsureDemoUserRequest,
      dreamy.identity.v1.EnsureDemoUserResponse> getEnsureDemoUserMethod() {
    io.grpc.MethodDescriptor<dreamy.identity.v1.EnsureDemoUserRequest, dreamy.identity.v1.EnsureDemoUserResponse> getEnsureDemoUserMethod;
    if ((getEnsureDemoUserMethod = IdentityGateGrpc.getEnsureDemoUserMethod) == null) {
      synchronized (IdentityGateGrpc.class) {
        if ((getEnsureDemoUserMethod = IdentityGateGrpc.getEnsureDemoUserMethod) == null) {
          IdentityGateGrpc.getEnsureDemoUserMethod = getEnsureDemoUserMethod =
              io.grpc.MethodDescriptor.<dreamy.identity.v1.EnsureDemoUserRequest, dreamy.identity.v1.EnsureDemoUserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "EnsureDemoUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.EnsureDemoUserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  dreamy.identity.v1.EnsureDemoUserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityGateMethodDescriptorSupplier("EnsureDemoUser"))
              .build();
        }
      }
    }
    return getEnsureDemoUserMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static IdentityGateStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityGateStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityGateStub>() {
        @java.lang.Override
        public IdentityGateStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityGateStub(channel, callOptions);
        }
      };
    return IdentityGateStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static IdentityGateBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityGateBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityGateBlockingStub>() {
        @java.lang.Override
        public IdentityGateBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityGateBlockingStub(channel, callOptions);
        }
      };
    return IdentityGateBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static IdentityGateFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityGateFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityGateFutureStub>() {
        @java.lang.Override
        public IdentityGateFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityGateFutureStub(channel, callOptions);
        }
      };
    return IdentityGateFutureStub.newStub(factory, channel);
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
     * store 会话有效性(热路径,Java StoreJwtFilter 每请求调用)。
     * Rust 侧 Redis-first:命中即返回;撤销即时 DEL 失效;Redis 不可用回落 DB。
     * </pre>
     */
    default void validateStoreSession(dreamy.identity.v1.ValidateStoreSessionRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateStoreSessionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getValidateStoreSessionMethod(), responseObserver);
    }

    /**
     * <pre>
     * admin 会话有效性(含管理员状态复核,对齐 Java SessionValidator.isAdminSessionValid)。
     * </pre>
     */
    default void validateAdminSession(dreamy.identity.v1.ValidateAdminSessionRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateAdminSessionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getValidateAdminSessionMethod(), responseObserver);
    }

    /**
     * <pre>
     * 管理员实时权限集(Java PermissionAspect 每次受注解保护的请求调用)。
     * admin 不存在 → 返回空列表(对齐 Java resolvePermissions 语义),不用 NOT_FOUND。
     * Rust 侧 Redis 缓存 + 角色/权限/管理员变更时主动失效(变更即时生效语义保留)。
     * </pre>
     */
    default void resolvePermissions(dreamy.identity.v1.ResolvePermissionsRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ResolvePermissionsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getResolvePermissionsMethod(), responseObserver);
    }

    /**
     * <pre>
     * 通用查询:精确查找(单值),返回固定脱敏记录 UserRecord。
     * 未命中 → NOT_FOUND。
     * </pre>
     */
    default void getUser(dreamy.identity.v1.GetUserRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.GetUserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetUserMethod(), responseObserver);
    }

    /**
     * <pre>
     * 通用查询:类型化条件(MyBatis-Lambda 式枚举列 × 枚举操作符)+ 分页。
     * 服务端强制追加 id 升序稳定兜底;LIKE 仅前缀匹配(防全表扫描)。
     * </pre>
     */
    default void listUsers(dreamy.identity.v1.ListUsersRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ListUsersResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListUsersMethod(), responseObserver);
    }

    /**
     * <pre>
     * demo 种子通道(Java ShowroomSeedInitializer/ReviewSeedInitializer 建 demo 用户用)。
     * 幂等:按 email 归并,已存在直接返回既有 user_id。
     * </pre>
     */
    default void ensureDemoUser(dreamy.identity.v1.EnsureDemoUserRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.EnsureDemoUserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getEnsureDemoUserMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service IdentityGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static abstract class IdentityGateImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return IdentityGateGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service IdentityGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class IdentityGateStub
      extends io.grpc.stub.AbstractAsyncStub<IdentityGateStub> {
    private IdentityGateStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityGateStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityGateStub(channel, callOptions);
    }

    /**
     * <pre>
     * store 会话有效性(热路径,Java StoreJwtFilter 每请求调用)。
     * Rust 侧 Redis-first:命中即返回;撤销即时 DEL 失效;Redis 不可用回落 DB。
     * </pre>
     */
    public void validateStoreSession(dreamy.identity.v1.ValidateStoreSessionRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateStoreSessionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getValidateStoreSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * admin 会话有效性(含管理员状态复核,对齐 Java SessionValidator.isAdminSessionValid)。
     * </pre>
     */
    public void validateAdminSession(dreamy.identity.v1.ValidateAdminSessionRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateAdminSessionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getValidateAdminSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 管理员实时权限集(Java PermissionAspect 每次受注解保护的请求调用)。
     * admin 不存在 → 返回空列表(对齐 Java resolvePermissions 语义),不用 NOT_FOUND。
     * Rust 侧 Redis 缓存 + 角色/权限/管理员变更时主动失效(变更即时生效语义保留)。
     * </pre>
     */
    public void resolvePermissions(dreamy.identity.v1.ResolvePermissionsRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ResolvePermissionsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getResolvePermissionsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 通用查询:精确查找(单值),返回固定脱敏记录 UserRecord。
     * 未命中 → NOT_FOUND。
     * </pre>
     */
    public void getUser(dreamy.identity.v1.GetUserRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.GetUserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 通用查询:类型化条件(MyBatis-Lambda 式枚举列 × 枚举操作符)+ 分页。
     * 服务端强制追加 id 升序稳定兜底;LIKE 仅前缀匹配(防全表扫描)。
     * </pre>
     */
    public void listUsers(dreamy.identity.v1.ListUsersRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.ListUsersResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListUsersMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * demo 种子通道(Java ShowroomSeedInitializer/ReviewSeedInitializer 建 demo 用户用)。
     * 幂等:按 email 归并,已存在直接返回既有 user_id。
     * </pre>
     */
    public void ensureDemoUser(dreamy.identity.v1.EnsureDemoUserRequest request,
        io.grpc.stub.StreamObserver<dreamy.identity.v1.EnsureDemoUserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getEnsureDemoUserMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service IdentityGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class IdentityGateBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<IdentityGateBlockingStub> {
    private IdentityGateBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityGateBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityGateBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * store 会话有效性(热路径,Java StoreJwtFilter 每请求调用)。
     * Rust 侧 Redis-first:命中即返回;撤销即时 DEL 失效;Redis 不可用回落 DB。
     * </pre>
     */
    public dreamy.identity.v1.ValidateStoreSessionResponse validateStoreSession(dreamy.identity.v1.ValidateStoreSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getValidateStoreSessionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * admin 会话有效性(含管理员状态复核,对齐 Java SessionValidator.isAdminSessionValid)。
     * </pre>
     */
    public dreamy.identity.v1.ValidateAdminSessionResponse validateAdminSession(dreamy.identity.v1.ValidateAdminSessionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getValidateAdminSessionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 管理员实时权限集(Java PermissionAspect 每次受注解保护的请求调用)。
     * admin 不存在 → 返回空列表(对齐 Java resolvePermissions 语义),不用 NOT_FOUND。
     * Rust 侧 Redis 缓存 + 角色/权限/管理员变更时主动失效(变更即时生效语义保留)。
     * </pre>
     */
    public dreamy.identity.v1.ResolvePermissionsResponse resolvePermissions(dreamy.identity.v1.ResolvePermissionsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getResolvePermissionsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 通用查询:精确查找(单值),返回固定脱敏记录 UserRecord。
     * 未命中 → NOT_FOUND。
     * </pre>
     */
    public dreamy.identity.v1.GetUserResponse getUser(dreamy.identity.v1.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 通用查询:类型化条件(MyBatis-Lambda 式枚举列 × 枚举操作符)+ 分页。
     * 服务端强制追加 id 升序稳定兜底;LIKE 仅前缀匹配(防全表扫描)。
     * </pre>
     */
    public dreamy.identity.v1.ListUsersResponse listUsers(dreamy.identity.v1.ListUsersRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListUsersMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * demo 种子通道(Java ShowroomSeedInitializer/ReviewSeedInitializer 建 demo 用户用)。
     * 幂等:按 email 归并,已存在直接返回既有 user_id。
     * </pre>
     */
    public dreamy.identity.v1.EnsureDemoUserResponse ensureDemoUser(dreamy.identity.v1.EnsureDemoUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getEnsureDemoUserMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service IdentityGate.
   * <pre>
   * ----------------------------------------------------------------------------
   * 服务定义
   * ----------------------------------------------------------------------------
   * </pre>
   */
  public static final class IdentityGateFutureStub
      extends io.grpc.stub.AbstractFutureStub<IdentityGateFutureStub> {
    private IdentityGateFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityGateFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityGateFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * store 会话有效性(热路径,Java StoreJwtFilter 每请求调用)。
     * Rust 侧 Redis-first:命中即返回;撤销即时 DEL 失效;Redis 不可用回落 DB。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.ValidateStoreSessionResponse> validateStoreSession(
        dreamy.identity.v1.ValidateStoreSessionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getValidateStoreSessionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * admin 会话有效性(含管理员状态复核,对齐 Java SessionValidator.isAdminSessionValid)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.ValidateAdminSessionResponse> validateAdminSession(
        dreamy.identity.v1.ValidateAdminSessionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getValidateAdminSessionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 管理员实时权限集(Java PermissionAspect 每次受注解保护的请求调用)。
     * admin 不存在 → 返回空列表(对齐 Java resolvePermissions 语义),不用 NOT_FOUND。
     * Rust 侧 Redis 缓存 + 角色/权限/管理员变更时主动失效(变更即时生效语义保留)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.ResolvePermissionsResponse> resolvePermissions(
        dreamy.identity.v1.ResolvePermissionsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getResolvePermissionsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 通用查询:精确查找(单值),返回固定脱敏记录 UserRecord。
     * 未命中 → NOT_FOUND。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.GetUserResponse> getUser(
        dreamy.identity.v1.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 通用查询:类型化条件(MyBatis-Lambda 式枚举列 × 枚举操作符)+ 分页。
     * 服务端强制追加 id 升序稳定兜底;LIKE 仅前缀匹配(防全表扫描)。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.ListUsersResponse> listUsers(
        dreamy.identity.v1.ListUsersRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListUsersMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * demo 种子通道(Java ShowroomSeedInitializer/ReviewSeedInitializer 建 demo 用户用)。
     * 幂等:按 email 归并,已存在直接返回既有 user_id。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<dreamy.identity.v1.EnsureDemoUserResponse> ensureDemoUser(
        dreamy.identity.v1.EnsureDemoUserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getEnsureDemoUserMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_VALIDATE_STORE_SESSION = 0;
  private static final int METHODID_VALIDATE_ADMIN_SESSION = 1;
  private static final int METHODID_RESOLVE_PERMISSIONS = 2;
  private static final int METHODID_GET_USER = 3;
  private static final int METHODID_LIST_USERS = 4;
  private static final int METHODID_ENSURE_DEMO_USER = 5;

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
        case METHODID_VALIDATE_STORE_SESSION:
          serviceImpl.validateStoreSession((dreamy.identity.v1.ValidateStoreSessionRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateStoreSessionResponse>) responseObserver);
          break;
        case METHODID_VALIDATE_ADMIN_SESSION:
          serviceImpl.validateAdminSession((dreamy.identity.v1.ValidateAdminSessionRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.ValidateAdminSessionResponse>) responseObserver);
          break;
        case METHODID_RESOLVE_PERMISSIONS:
          serviceImpl.resolvePermissions((dreamy.identity.v1.ResolvePermissionsRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.ResolvePermissionsResponse>) responseObserver);
          break;
        case METHODID_GET_USER:
          serviceImpl.getUser((dreamy.identity.v1.GetUserRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.GetUserResponse>) responseObserver);
          break;
        case METHODID_LIST_USERS:
          serviceImpl.listUsers((dreamy.identity.v1.ListUsersRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.ListUsersResponse>) responseObserver);
          break;
        case METHODID_ENSURE_DEMO_USER:
          serviceImpl.ensureDemoUser((dreamy.identity.v1.EnsureDemoUserRequest) request,
              (io.grpc.stub.StreamObserver<dreamy.identity.v1.EnsureDemoUserResponse>) responseObserver);
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
          getValidateStoreSessionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.ValidateStoreSessionRequest,
              dreamy.identity.v1.ValidateStoreSessionResponse>(
                service, METHODID_VALIDATE_STORE_SESSION)))
        .addMethod(
          getValidateAdminSessionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.ValidateAdminSessionRequest,
              dreamy.identity.v1.ValidateAdminSessionResponse>(
                service, METHODID_VALIDATE_ADMIN_SESSION)))
        .addMethod(
          getResolvePermissionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.ResolvePermissionsRequest,
              dreamy.identity.v1.ResolvePermissionsResponse>(
                service, METHODID_RESOLVE_PERMISSIONS)))
        .addMethod(
          getGetUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.GetUserRequest,
              dreamy.identity.v1.GetUserResponse>(
                service, METHODID_GET_USER)))
        .addMethod(
          getListUsersMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.ListUsersRequest,
              dreamy.identity.v1.ListUsersResponse>(
                service, METHODID_LIST_USERS)))
        .addMethod(
          getEnsureDemoUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              dreamy.identity.v1.EnsureDemoUserRequest,
              dreamy.identity.v1.EnsureDemoUserResponse>(
                service, METHODID_ENSURE_DEMO_USER)))
        .build();
  }

  private static abstract class IdentityGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    IdentityGateBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return dreamy.identity.v1.Identity.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("IdentityGate");
    }
  }

  private static final class IdentityGateFileDescriptorSupplier
      extends IdentityGateBaseDescriptorSupplier {
    IdentityGateFileDescriptorSupplier() {}
  }

  private static final class IdentityGateMethodDescriptorSupplier
      extends IdentityGateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    IdentityGateMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (IdentityGateGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new IdentityGateFileDescriptorSupplier())
              .addMethod(getValidateStoreSessionMethod())
              .addMethod(getValidateAdminSessionMethod())
              .addMethod(getResolvePermissionsMethod())
              .addMethod(getGetUserMethod())
              .addMethod(getListUsersMethod())
              .addMethod(getEnsureDemoUserMethod())
              .build();
        }
      }
    }
    return result;
  }
}
