package com.toy.nar.app.mobile.push;

import java.util.List;

public interface MobilePushGateway {

	boolean isAvailable();

	MobilePushResult send(List<String> tokens, MobilePushMessage message);
}
