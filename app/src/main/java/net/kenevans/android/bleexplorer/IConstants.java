//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.bleexplorer;

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
    /**
     * Tag to associate with log messages.
     */
    String TAG = "BLEExplorer";
    /**
     * Name of the package for this application.
     */
    String PACKAGE_NAME = "net.kenevans.android.bleexplorer";

    /**
     * Default scan period for device scan.
     */
    long DEVICE_SCAN_PERIOD = 10000;

    /**
     * Request code for all permissions.
     */
    int REQ_ACCESS_PERMISSIONS = 1;
    /**
     * Code for requesting ACCESS_COARSE_LOCATION permission.
     */
    int PERMISSION_ACCESS_COARSE_LOCATION = 1;
    /**
     * The intent code for device name.
     */
    String DEVICE_NAME_CODE = PACKAGE_NAME + ".deviceName";
    /**
     * The intent code for device address.
     */
    String DEVICE_ADDRESS_CODE = PACKAGE_NAME
            + "deviceAddress";

    String LIST_NAME = "NAME";
    String LIST_UUID = "UUID";



}
