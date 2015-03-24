/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 3.0.5
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.google.fpl.liquidfun;

public class Version {
  private long swigCPtr;
  protected boolean swigCMemOwn;

  protected Version(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(Version obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        liquidfunJNI.delete_Version(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setMajor(int value) {
    liquidfunJNI.Version_major_set(swigCPtr, this, value);
  }

  public int getMajor() {
    return liquidfunJNI.Version_major_get(swigCPtr, this);
  }

  public void setMinor(int value) {
    liquidfunJNI.Version_minor_set(swigCPtr, this, value);
  }

  public int getMinor() {
    return liquidfunJNI.Version_minor_get(swigCPtr, this);
  }

  public void setRevision(int value) {
    liquidfunJNI.Version_revision_set(swigCPtr, this, value);
  }

  public int getRevision() {
    return liquidfunJNI.Version_revision_get(swigCPtr, this);
  }

  public Version() {
    this(liquidfunJNI.new_Version(), true);
  }

}