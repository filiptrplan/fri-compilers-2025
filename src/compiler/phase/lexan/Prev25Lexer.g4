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

// KEYWORDS

TYP         : 'typ' ;

// SYMBOLS

PLUS        : '+' ;
MINUS       : '-' ; 

// CONSTANTS

INTCONST    : (PLUS|MINUS)? [0-9]+ ;

// IDENTIFIER

IDENT       : [A-Za-z_][A-Za-z_0-9]* ;

WHITESPACE  : [ \n\r]+ -> skip ;
CHAR        : . ;

