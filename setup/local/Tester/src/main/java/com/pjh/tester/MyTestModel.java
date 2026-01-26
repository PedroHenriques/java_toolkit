package com.pjh.tester;

import java.util.ArrayList;
import java.util.List;

public class MyTestModel {
  public String name = "starting name";
  public int age = 0;
  public MyInnerTestModel inner = new MyInnerTestModel();
  public List<String> list = new ArrayList<>();

  public static class MyInnerTestModel {
    public String innerName = "starting inner name";
    public int innerAge = 1;
  }
}
