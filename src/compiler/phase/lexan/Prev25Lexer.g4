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

// CONSTANTS

INTCONST    : (PLUS|MINUS)? [0-9]+ ;

// SYMBOLS

AND         : '&' ;
OR          : '|' ;
EQ_EQ       : '==' ;
NEQ         : '!=' ;
LT          : '<' ;
GT          : '>' ;
LTE         : '<=' ;
GTE         : '>=' ;
STAR        : '*' ;
SLASH       : '/' ;
PERCENT     : '%' ;
PLUS        : '+' ;
MINUS       : '-' ;
BANG        : '!' ;
DOT         : '.' ;
POWER       : '^' ;
EQ          : '=' ;
COMMA       : ',' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;

// KEYWORDS

BOOL        : 'bool' ;
CHAR        : 'char' ;
DO          : 'do' ;
ELSE        : 'else' ;
END         : 'end' ;
FALSE       : 'false' ;
FUN         : 'fun' ;
IF          : 'if' ;
IN          : 'in' ;
INT         : 'int' ;
LET         : 'let' ;
NULL        : 'null' ;
RETURN      : 'return' ;
SIZEOF      : 'sizeof' ;
THEN        : 'then' ;
TRUE        : 'true' ;
TYP         : 'typ' ;
VAR         : 'var' ;
VOID        : 'void' ;
WHILE       : 'while' ;

// IDENTIFIER

IDENT       : [A-Za-z_][A-Za-z0-9_]* ;

// COMMENTS AND WHITESPACE

COMMENT : '#' ~[\r\n]* '\r'? '\n' -> skip ;
WHITESPACE  : [ \n\r]+ -> skip ;
