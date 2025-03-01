parser grammar Prev25Parser;

@header {

	package compiler.phase.synan;
	
	import java.util.*;
	import compiler.common.report.*;
	import compiler.phase.lexan.*;

}

@members {

	private Location loc(Token tok) { return new Location((LexAn.LocLogToken)tok); }
	private Location loc(Token     tok1, Token     tok2) { return new Location((LexAn.LocLogToken)tok1, (LexAn.LocLogToken)tok2); }
	private Location loc(Token     tok1, Locatable loc2) { return new Location((LexAn.LocLogToken)tok1, loc2); }
	private Location loc(Locatable loc1, Token     tok2) { return new Location(loc1, (LexAn.LocLogToken)tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }

}

options{
    tokenVocab=Prev25Lexer;
}

// MISC
id_colon_type 
    : (IDENT COLON type) (COMMA IDENT COLON type)* ;

// PROGRAM
source
	: def+ EOF
	;

// DEFINITIONS
def : TYP IDENT EQ type 
    | VAR IDENT COLON type
    | FUN IDENT LPAREN (id_colon_type)? RPAREN COLON type
    | FUN IDENT LPAREN (id_colon_type)? RPAREN COLON type EQ stmts
    ;

// STATEMENTS
stmts 
    : stmt COMMA stmts
    | stmt
    ;

stmt 
    : expr
    | expr EQ expr
    | RETURN expr
    | WHILE expr DO stmts? END
    | IF expr THEN stmts? END
    | IF expr THEN stmts? ELSE stmts? END
    | LET def+ IN stmts END 
    ; 

// TYPES
type 
    : IDENT
    | INT
    | CHAR
    | BOOL 
    | VOID
    | IDENT
    | LBRACKET INTCONST RBRACKET type
    | POWER type
    | LT id_colon_type GT
    | LBRACE id_colon_type RBRACE
    | LPAREN (type (COMMA type)*)? RPAREN COLON type
    ;

// EXPRESSIONS
expr : or ;
or : or OR and | and ;
and : and AND comp | comp ;
comp 
    : add EQ_EQ add 
    | add NEQ add
    | add LT add
    | add GT add
    | add GTE add
    | add LTE add
    | add
    ;
add 
    : add PLUS mul 
    | add MINUS mul
    | mul 
    ;
mul
    : mul STAR prefix
    | mul SLASH prefix
    | mul PERCENT prefix
    | prefix
    ;
prefix
    : PLUS prefix
    | MINUS prefix
    | BANG prefix
    | POWER prefix
    | postfix
    ;
postfix
    : postfix LPAREN (expr (COMMA expr)*)? RPAREN
    | postfix LBRACKET expr RBRACKET
    | postfix POWER
    | postfix DOT IDENT
    | const
    ;
const
    : INTCONST
    | CHARCONST
    | STRCONST
    | TRUE
    | FALSE
    | NULL
    | SIZEOF type
    | IDENT
    | LPAREN expr RPAREN
    | LBRACE expr COLON type RBRACE
    ;
