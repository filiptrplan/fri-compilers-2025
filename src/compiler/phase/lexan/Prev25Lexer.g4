lexer grammar Prev25Lexer;

@header {
	package compiler.phase.lexan;

	import compiler.common.report.*;
}

@members {
    @Override
	public LexAn.LocLogToken nextToken() {
		return (LexAn.LocLogToken) super.nextToken();
	}
}

WHITESPACE    : [ \n\r]+ -> skip ;
