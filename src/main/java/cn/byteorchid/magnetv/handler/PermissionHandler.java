package cn.byteorchid.magnetv.handler;

/**
 * created 2019/7/8 17:50
 */
public interface PermissionHandler<T> {

    T onPermissionGranted() throws Exception;
}
