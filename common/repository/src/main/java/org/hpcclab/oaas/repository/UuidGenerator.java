package org.hpcclab.oaas.repository;


import java.util.UUID;

//@ApplicationScoped
public class UuidGenerator implements IdGenerator{

  @Override
  public String generate() {
    return UUID.randomUUID().toString();
  }
}
