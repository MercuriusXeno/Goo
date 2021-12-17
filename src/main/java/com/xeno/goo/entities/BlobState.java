package com.xeno.goo.entities;

public enum BlobState {
	UNKNOWN, // pre-life, inoperable
	BLOB, // also drips
	SPLAT, // also runs
	FLOW, // downward streams only
	VAPOR // decay/waste, but still has effect on things it touches
}
