package com.toy.nar.app.youtube.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "feed", namespace = "http://www.w3.org/2005/Atom")
public class PubSubFeed {

	@JacksonXmlProperty(localName = "entry", namespace = "http://www.w3.org/2005/Atom")
	@JacksonXmlElementWrapper(useWrapping = false)
	private List<PubSubEntry> entries;
}
