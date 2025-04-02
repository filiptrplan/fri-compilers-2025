package compiler.phase.imcgen;

import compiler.phase.abstr.*;

/**
 * Intermediate code generator.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class ImcGenerator implements AST.FullVisitor<Object, Object> {

	// ----- Trees -----

	@Override
	public Object visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		for (final AST.Node node : nodes)
			if ((node != null) || (!compiler.Compiler.devMode()))
				node.accept(this, arg);
		return null;
	}

	// ----- Definitions -----

	@Override
	public Object visit(AST.TypDefn typDefn, Object arg) {
		if ((typDefn.type != null) || (!compiler.Compiler.devMode()))
			typDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.VarDefn varDefn, Object arg) {
		if ((varDefn.type != null) || (!compiler.Compiler.devMode()))
			varDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.DefFunDefn defFunDefn, Object arg) {
		if ((defFunDefn.pars != null) || (!compiler.Compiler.devMode()))
			defFunDefn.pars.accept(this, arg);
		if ((defFunDefn.type != null) || (!compiler.Compiler.devMode()))
			defFunDefn.type.accept(this, arg);
		if ((defFunDefn.stmts != null) || (!compiler.Compiler.devMode()))
			defFunDefn.stmts.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.ExtFunDefn extFunDefn, Object arg) {
		if ((extFunDefn.pars != null) || (!compiler.Compiler.devMode()))
			extFunDefn.pars.accept(this, arg);
		if ((extFunDefn.type != null) || (!compiler.Compiler.devMode()))
			extFunDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.ParDefn parDefn, Object arg) {
		if ((parDefn.type != null) || (!compiler.Compiler.devMode()))
			parDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.CompDefn compDefn, Object arg) {
		if ((compDefn.type != null) || (!compiler.Compiler.devMode()))
			compDefn.type.accept(this, arg);
		return null;
	}

	// ----- Statements -----

	@Override
	public Object visit(AST.LetStmt letStmt, Object arg) {
		if ((letStmt.defns != null) || (!compiler.Compiler.devMode()))
			letStmt.defns.accept(this, arg);
		if ((letStmt.stmts != null) || (!compiler.Compiler.devMode()))
			letStmt.stmts.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.AssignStmt assignStmt, Object arg) {
		if ((assignStmt.dstExpr != null) || (!compiler.Compiler.devMode()))
			assignStmt.dstExpr.accept(this, arg);
		if ((assignStmt.srcExpr != null) || (!compiler.Compiler.devMode()))
			assignStmt.srcExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.ExprStmt callStmt, Object arg) {
		if ((callStmt.expr != null) || (!compiler.Compiler.devMode()))
			callStmt.expr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.IfThenStmt ifThenStmt, Object arg) {
		if ((ifThenStmt.condExpr != null) || (!compiler.Compiler.devMode()))
			ifThenStmt.condExpr.accept(this, arg);
		if ((ifThenStmt.thenStmt != null) || (!compiler.Compiler.devMode()))
			ifThenStmt.thenStmt.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.IfThenElseStmt ifThenElseStmt, Object arg) {
		if ((ifThenElseStmt.condExpr != null) || (!compiler.Compiler.devMode()))
			ifThenElseStmt.condExpr.accept(this, arg);
		if ((ifThenElseStmt.thenStmt != null) || (!compiler.Compiler.devMode()))
			ifThenElseStmt.thenStmt.accept(this, arg);
		if ((ifThenElseStmt.elseStmt != null) || (!compiler.Compiler.devMode()))
			ifThenElseStmt.elseStmt.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.ReturnStmt returnStmt, Object arg) {
		if ((returnStmt.retExpr != null) || (!compiler.Compiler.devMode()))
			returnStmt.retExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.WhileStmt whileStmt, Object arg) {
		if ((whileStmt.condExpr != null) || (!compiler.Compiler.devMode()))
			whileStmt.condExpr.accept(this, arg);
		if ((whileStmt.stmts != null) || (!compiler.Compiler.devMode()))
			whileStmt.stmts.accept(this, arg);
		return null;
	}

	// ----- Types -----

	@Override
	public Object visit(AST.AtomType atomType, Object arg) {
		return null;
	}

	@Override
	public Object visit(AST.ArrType arrType, Object arg) {
		if ((arrType.elemType != null) || (!compiler.Compiler.devMode()))
			arrType.elemType.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.PtrType ptrType, Object arg) {
		if ((ptrType.baseType != null) || (!compiler.Compiler.devMode()))
			ptrType.baseType.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.StrType strType, Object arg) {
		if ((strType.comps != null) || (!compiler.Compiler.devMode()))
			strType.comps.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.UniType uniType, Object arg) {
		if ((uniType.comps != null) || (!compiler.Compiler.devMode()))
			uniType.comps.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.FunType funType, Object arg) {
		if ((funType.parTypes != null) || (!compiler.Compiler.devMode()))
			funType.parTypes.accept(this, arg);
		if ((funType.resType != null) || (!compiler.Compiler.devMode()))
			funType.resType.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.NameType nameType, Object arg) {
		return null;
	}

	// ----- Expressions -----

	@Override
	public Object visit(AST.ArrExpr arrExpr, Object arg) {
		if ((arrExpr.arrExpr != null) || (!compiler.Compiler.devMode()))
			arrExpr.arrExpr.accept(this, arg);
		if ((arrExpr.idx != null) || (!compiler.Compiler.devMode()))
			arrExpr.idx.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.AtomExpr atomExpr, Object arg) {
		return null;
	}

	@Override
	public Object visit(AST.BinExpr binExpr, Object arg) {
		if ((binExpr.fstExpr != null) || (!compiler.Compiler.devMode()))
			binExpr.fstExpr.accept(this, arg);
		if ((binExpr.sndExpr != null) || (!compiler.Compiler.devMode()))
			binExpr.sndExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.CallExpr callExpr, Object arg) {
		if ((callExpr.funExpr != null) || (!compiler.Compiler.devMode()))
			callExpr.funExpr.accept(this, arg);
		if ((callExpr.argExprs != null) || (!compiler.Compiler.devMode()))
			callExpr.argExprs.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.CastExpr castExpr, Object arg) {
		if ((castExpr.type != null) || (!compiler.Compiler.devMode()))
			castExpr.type.accept(this, arg);
		if ((castExpr.expr != null) || (!compiler.Compiler.devMode()))
			castExpr.expr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.CompExpr compExpr, Object arg) {
		if ((compExpr.recExpr != null) || (!compiler.Compiler.devMode()))
			compExpr.recExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.NameExpr nameExpr, Object arg) {
		return null;
	}

	@Override
	public Object visit(AST.PfxExpr pfxExpr, Object arg) {
		if ((pfxExpr.subExpr != null) || (!compiler.Compiler.devMode()))
			pfxExpr.subExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.SfxExpr sfxExpr, Object arg) {
		if ((sfxExpr.subExpr != null) || (!compiler.Compiler.devMode()))
			sfxExpr.subExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AST.SizeExpr sizeExpr, Object arg) {
		if ((sizeExpr.type != null) || (!compiler.Compiler.devMode()))
			sizeExpr.type.accept(this, arg);
		return null;
	}
}
